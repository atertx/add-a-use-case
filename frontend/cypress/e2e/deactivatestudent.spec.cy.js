// UC-STU-deactivate-student: the Deactivate action in the Edit a student dialog, and the peer evaluation form's
// teammate list (BR-deactivated-student-evaluation). The student API calls are stubbed, so these tests check what the
// SPA does with each answer and what it sends; the backend's own tests cover authorization, 3a, 3b and the rules.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const eric = {
  id: 5,
  username: 'e.hudson@abc.edu',
  firstName: 'Eric',
  lastName: 'Hudson',
  email: 'e.hudson@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1',
  deactivatedOn: null,
  evaluable: null
}
const ericDeactivated = { ...eric, enabled: false, deactivatedOn: '2023-08-20' }

const ok = (data) => ({ flag: true, code: 200, message: 'OK', data })
const page = (content) =>
  ok({
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: content.length === 0
  })

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', { body: page([eric]) }).as('search')
    cy.intercept('GET', '**/students/5', { body: ok(eric) }).as('findEric')
    cy.intercept('POST', '**/students/5/deactivation', { body: ok(ericDeactivated) }).as(
      'deactivate'
    )
    cy.visit('/students')
    cy.wait('@search')
    cy.get('.el-table__row').contains('Eric').parents('.el-table__row').find('button').click()
    cy.wait('@findEric')
  })

  it('warns of every consequence before deactivating (step 4)', () => {
    cy.get('[data-cy=deactivate-student]').click()

    cy.get('.el-message-box').should('contain', 'Deactivate Eric Hudson?')
    cy.get('[data-cy=deactivation-consequences]').within(() => {
      ;['log in', 'team', 'owes', 'evaluate', 'report', 'kept', 'reactivated'].forEach((phrase) =>
        cy.contains(phrase)
      )
    })
    cy.get('.el-message-box').contains('button', 'Deactivate').should('be.visible')
    cy.get('.el-message-box').contains('button', 'Cancel').should('be.visible')
  })

  it('sends nothing when the warning is cancelled (4a)', () => {
    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-dialog').should('be.visible').and('contain', 'Edit a student')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('deactivates, reports success and shows the new status (steps 5-6)', () => {
    cy.get('[data-cy=deactivate-student]').click()
    cy.intercept('POST', '**/students/search*', { body: page([ericDeactivated]) }).as('reload')
    cy.intercept('GET', '**/students/5', { body: ok(ericDeactivated) }).as('findDeactivated')
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate').its('request.body').should('be.oneOf', [undefined, null, ''])
    cy.contains('.el-message', 'Eric Hudson has been deactivated.').should('be.visible')
    cy.get('.el-dialog').should('not.exist')
    cy.wait('@reload')
    cy.get('.el-table__row')
      .contains('Eric')
      .parents('.el-table__row')
      .should('contain', 'Disabled')

    // Reopening her shows the date in place of the Deactivate button
    cy.get('.el-table__row').contains('Eric').parents('.el-table__row').find('button').click()
    cy.wait('@findDeactivated')
    cy.get('[data-cy=deactivated-on]').should('contain', 'Deactivated on 2023-08-20')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })

  it('closes and reloads when the student is already deactivated (3b)', () => {
    cy.intercept('POST', '**/students/5/deactivation', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'This student is already deactivated.' }
    }).as('conflict')
    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@conflict')
    cy.contains('.el-message', 'This student is already deactivated.').should('be.visible')
    cy.get('.el-dialog').should('not.exist')
  })
})

describe('Peer evaluation form offers only evaluable teammates (BR-deactivated-student-evaluation)', () => {
  it('omits a teammate the server marks not evaluable for the rated week', () => {
    cy.login('j.smith@abc.edu', '123456')
    const teammate = (id, firstName, lastName, evaluable) => ({
      ...eric,
      id,
      firstName,
      lastName,
      email: `${firstName}@abc.edu`,
      username: `${firstName}@abc.edu`,
      deactivatedOn: null,
      evaluable
    })
    cy.intercept('GET', '**/students/teams/*', {
      body: ok([
        teammate(4, 'John', 'Smith', true),
        teammate(5, 'Eric', 'Hudson', true),
        teammate(6, 'Jerry', 'Moon', false)
      ])
    }).as('teammates')
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', { body: ok([]) }).as('existing')
    cy.visit('/submit-evaluations')
    cy.wait('@teammates')
    cy.wait('@existing')

    cy.get('.el-table').should('contain', 'John Smith').and('contain', 'Eric Hudson')
    cy.get('.el-table').should('not.contain', 'Jerry Moon')
  })
})
