<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# CorDapp ConfidentialExample

This is an example of a CorDapp that uses the confidential identity support in Corda 4.3. This 
differs from previous implementation's of confidential identities in earlier versions of Corda.

More information here:
https://github.com/corda/confidential-identities

# Scope
This example demonstrates the confidential identity package.

## Out of scope
This example does not extend to using confidential identities with accounts

# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Flows

This CorDapp contains 3 flows.
- Create
- Update
- ShareKeys

## Create
This flow takes two parties to share a simple state with. The state contains a string
and includes all parties anonymously. Therefore the two other parties do not know the well
known identities of each other.

## Update
This flow updates the string value of the state. Although any party may update this state,
following creation only the creator knows the well known identities of all participants. 
This means that only the creator can update the state until such time as they choose to 
share the keys with all participants.

## ShareKeys
This flow shares the anonymous keys with all parties. After which time any party will be able
to update the state. Again, at the point of creation only the creator knows the identity of
all parties, so only they can successfully run this flow. Though re-running this flow doesn't
achieve anything of practical use, technically any party can run this flow after the
creator has run it.

## Code

Look in workflows/src/main/kotlin/com/confidentialexample/flows/Flows.kt for flow implementation.

# Usage

A single test in workflows/src/test/kotlin/com/confidentialexample/FlowTests.kt walks through 
the flows, and what succeeds and fails at each step until keys are shared.


