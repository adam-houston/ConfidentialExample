package com.confidentialexample

import com.confidentialexample.flows.*
import com.confidentialexample.states.ConfidentialExampleState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import kotlin.test.assertEquals

class FlowTests {
    private val network =
            MockNetwork(
                MockNetworkParameters(cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.confidentialexample.contracts"),
                        TestCordapp.findCordapp("com.confidentialexample.flows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows")
                    ),
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
                )
            )

    private val a = network.createNode()
    private val b = network.createNode()
    private val c = network.createNode()

    init {
        listOf(a, b, c).forEach {
            it.registerInitiatedFlow(Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun TestFlow() {

        // Create a new state
        val partyB = b.info.legalIdentities.first()
        val partyC = c.info.legalIdentities.first()
        val flow = Create("Demo", partyB, partyC)
        val future = a.startFlow(flow)

        var linearId = UniqueIdentifier()

        network.runNetwork()
        future.getOrThrow()
        listOf(a, b, c).forEach {
            val states = it.services.vaultService.queryBy(ConfidentialExampleState::class.java).states
            assert(states.size == 1)
            states.forEach {
                st -> println(st.state.data)
                assert(st.state.data.data == "Demo")
                linearId = st.state.data.linearId
            }
        }

        // Successful - PartyA knows the true identity of all parties
        with (a) {
            val name = this.info.legalIdentities.first().name
            val theData = "Data $name"
            val loopFlow = Update(linearId, theData)
            val loopFuture = this.startFlow(loopFlow)

            network.runNetwork()
            loopFuture.getOrThrow()

            listOf(a, b, c).forEach {
                node ->
                node.services.vaultService.queryBy(ConfidentialExampleState::class.java).states.forEach {
                    st ->
                    assert(st.state.data.data == theData)
                }
            }
        }

        // These will fail - parties b and c do not know the real identities of each other
        listOf(b, c).forEach {
            val name = it.info.legalIdentities.first().name
            val loopFlow = Update(linearId,"Data $name")
            val loopFuture = it.startFlow(loopFlow)

            network.runNetwork()

            var failed = false

            try {
                loopFuture.getOrThrow()
            } catch (e : IllegalStateException) {
                failed = true
            }

            assert(failed)
        }

        // Parties B nad C can try to share the identities, but this will fail as they do not know who each other is
        listOf(b, c).forEach {
            val loopFlow = ShareKeys(linearId)
            val loopFuture = it.startFlow(loopFlow)
            network.runNetwork()

            var failed = false
            try {
                loopFuture.getOrThrow()
            } catch (e : IllegalStateException) {
                failed = true
            }
            assert(failed)
        }

        // now let party A share the identities
        with (a) {
            val loopFlow = ShareKeys(linearId)
            val loopFuture = this.startFlow(loopFlow)
            network.runNetwork()
            loopFuture.getOrThrow()
        }

        // This does nothing of use, but Parties B & C can now share the identities, because they know about each other
        listOf(b, c).forEach {
            val loopFlow = ShareKeys(linearId)
            val loopFuture = it.startFlow(loopFlow)
            network.runNetwork()

            var success = false
            try {
                loopFuture.getOrThrow()
                success = true
            } catch (e : IllegalStateException) {
            }
            assert(success)
        }

        // and this time b and c can update as they know the true identities
        listOf(b, c).forEach {
            val name = it.info.legalIdentities.first().name
            val theData = "Data $name"
            val loopFlow = Update(linearId,theData)
            val loopFuture = it.startFlow(loopFlow)

            network.runNetwork()

            var succeeded = true

            try {
                loopFuture.getOrThrow()
            } catch (e : IllegalStateException) {
                succeeded = false
            }

            assert(succeeded)

            listOf(a, b, c).forEach {
                node ->
                    node.services.vaultService.queryBy(ConfidentialExampleState::class.java).states.forEach {
                    st ->
                        assert(st.state.data.data == theData)
                }
            }
        }
    }
}
