package com.confidentialexample.contracts

import com.confidentialexample.states.ConfidentialExampleState
import net.corda.core.contracts.LinearPointer
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

val ALICE = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))
val BOB = TestIdentity(CordaX500Name(organisation = "Bob", locality = "TestCity", country = "US"))

class ContractTests {
    private val ledgerServices = MockServices()

    @Test
    fun `dummy test`() {
//        ledgerServices.ledger {
//            transaction {
//                output(ConfidentialExampleContract.ID, ConfidentialExampleState("Test1", listOf(ALICE.party, BOB.party)))
//                command(listOf(ALICE.publicKey, BOB.publicKey), ConfidentialExampleContract.Commands.Action()) // Correct type.
//                this.verifies()
//            }
//        }
    }
}
