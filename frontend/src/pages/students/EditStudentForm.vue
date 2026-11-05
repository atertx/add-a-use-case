<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:">
      <template v-if="studentBeingEdited.enabled">
        <el-tag type="success" style="margin-right: 12px">Enabled</el-tag>
        <el-button type="danger" plain data-cy="deactivate-student" @click="deactivate()">
          Deactivate
        </el-button>
      </template>
      <template v-else>
        <span data-cy="deactivated-on" style="margin-right: 12px">
          Deactivated on {{ studentBeingEdited.deactivatedOn }}
        </span>
        <!-- Interim, until UC-STU-reactivate-student is built: re-enables through the generic edit -->
        <el-button type="primary" plain data-cy="reenable-student" @click="reEnable()">
          Re-enable
        </el-button>
      </template>
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

const studentForm = ref<FormInstance>() // Form reference

// The student as stored when the dialog loaded, untouched by edits in the form fields
let loadedStudent: Student | null = null

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.deactivatedOn = result.data.deactivatedOn
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
  loadedStudent = { ...result.data }
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    enabled: studentBeingEdited.value.enabled, // The stored status, sent back unchanged
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}

/**
 * The consequences of deactivating a student, one sentence each (UC-STU-deactivate-student, Associated Information).
 */
function deactivationConsequences(name: string) {
  return h('div', { 'data-cy': 'deactivation-consequences' }, [
    h(
      'p',
      `${name} will no longer be able to log in, and any session already open stops working at its next request.`
    ),
    h('p', `${name} stays on the team.`),
    h(
      'p',
      `${name} no longer owes weekly activity reports or peer evaluations and is not reminded of them, and teammates no longer owe evaluations of ${name}.`
    ),
    h('p', `Teammates cannot evaluate ${name} for this week or any later week.`),
    h('p', `The section's peer evaluation report shows ${name} only for earlier weeks.`),
    h('p', `${name}'s account and previously submitted work are kept.`),
    h('p', 'The account can be reactivated later. Deactivation is not deletion.')
  ])
}

// UC-STU-deactivate-student, steps 3-6
async function deactivate() {
  const student = studentBeingEdited.value
  const name = `${student.firstName} ${student.lastName}`

  // Step 4: warn and ask for confirmation
  try {
    await ElMessageBox.confirm(deactivationConsequences(name), `Deactivate ${name}?`, {
      confirmButtonText: 'Deactivate',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })
  } catch {
    return // 4a: cancelled; nothing is sent and the dialog stays open
  }

  // Steps 5-6
  try {
    await deactivateStudent(student.id as number)
  } catch (error) {
    // 3b: request.ts has toasted "This student is already deactivated."; close so the list shows her current status.
    // 3a: request.ts has toasted and opened the forbidden page, so there is nothing to close.
    if ((error as { response?: { status?: number } }).response?.status === 409) {
      emit('close-dialog')
    }
    return
  }

  ElMessage.success(`${name} has been deactivated.`)
  emit('close-dialog')
}

// Interim re-enable, until UC-STU-reactivate-student is built
// Sends the stored values, so unsaved edits in the form fields are not saved as a side effect
async function reEnable() {
  if (!loadedStudent) return
  const student = loadedStudent
  try {
    await updateStudent({
      id: student.id,
      username: student.username,
      enabled: true,
      firstName: student.firstName,
      lastName: student.lastName,
      email: student.email
    })
  } catch {
    return // request.ts has already toasted the failure; the dialog stays open
  }
  ElMessage.success(`${student.firstName} ${student.lastName} has been re-enabled.`)
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
