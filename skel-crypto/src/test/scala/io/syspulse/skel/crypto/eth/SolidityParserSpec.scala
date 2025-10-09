package io.syspulse.skel.crypto.eth

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object TestEvents {
  val EVENTS_DEFAULT = """

event AddedToSanctionsList(address indexed account);
event RemovedFromSanctionsList(address indexed account);
event IdentityRegistered(bytes32 indexed ccid, address indexed account);
event IdentityRemoved(bytes32 indexed ccid, address indexed account);

event CredentialRegistered(bytes32 indexed ccid, bytes32 indexed credentialTypeId, uint40 expiresAt, bytes credentialData);
event CredentialRemoved(bytes32 indexed ccid, bytes32 indexed credentialTypeId);
event CredentialRenewed(bytes32 indexed ccid, bytes32 indexed credentialTypeId, uint40 expiresAt);

event CredentialRequirementAdded(bytes32 indexed requirementId, bytes32[] credentialTypeIds, uint256 minValidations);
event CredentialRequirementRemoved(bytes32 indexed requirementId, bytes32[] credentialTypeIds, uint256 minValidations);
event CredentialSourceAdded(bytes32 indexed credentialTypeId,address indexed identityRegistry,address indexed credentialRegistry,address dataValidator);
event CredentialSourceRemoved(bytes32 indexed credentialTypeId,address indexed identityRegistry,address indexed credentialRegistry,address dataValidator);

event DataSourceSet(bytes32 indexed dataSourceId,address indexed source,bytes4 indexed schemaId,bytes metadata);

event OffchainDataRequested(bytes32 indexed dataSourceId, string endpoint);

event ActionCompleted(address indexed caller);

event TargetAttached(address indexed target);
event TargetDetached(address indexed target);
event PolicyAdded(address indexed target, bytes4 indexed selector, address policy);
event PolicyRemoved(address indexed target, bytes4 indexed selector, address policy);
event ExtractorSet(bytes4 indexed selector, address indexed extractor);
event PolicyParametersSet(address indexed policy, bytes[] parameters);
event DefaultPolicyResultSet(PolicyResult defaultPolicy);
event TargetDefaultPolicyResultSet(address indexed target, PolicyResult defaultPolicy);

event PolicyCreated(address policy);

event PriceFeedSet(address priceFeed);

event PolicyEngineAttached(address indexed policyEngine);

event SignerAdded(address signer);
event SignerRemoved(address signer);

event OperationAllowanceGrantedToRole(bytes4 operation, bytes32 role);
event OperationAllowanceRemovedFromRole(bytes4 operation, bytes32 role);

event ReservesFeedSet(address reservesFeed);
event ReserveMarginSet(ReserveMarginMode mode, uint256 amount);
event MaxStalenessSecondsSet(uint256 maxStalenessSeconds);

event MaxVolumeSet(uint256 maxAmount);
event MinVolumeSet(uint256 minAmount);

event MaxAmountSet(uint256 maxAmount);
event TimePeriodDurationSet(uint256 timePeriodDuration);

event PolicyAllowedExecuted(uint256 value);

event Frozen(address indexed account, uint256 amount);
event Unfrozen(address indexed account, uint256 amount);
event ForceTransfer(address indexed from, address indexed to, uint256 amount);

event ModuleInteraction(address indexed target, bytes4 selector);
event TokenBound(address _token);
event TokenUnbound(address _token);
event ModuleAdded(address indexed _module);
event ModuleRemoved(address indexed _module);

event ClaimTopicAdded(uint256 indexed claimTopic);
event ClaimTopicRemoved(uint256 indexed claimTopic);

event ClaimTopicsRegistrySet(address indexed claimTopicsRegistry);
event IdentityStorageSet(address indexed identityStorage);
event TrustedIssuersRegistrySet(address indexed trustedIssuersRegistry);
event IdentityRegistered(address indexed investorAddress, IIdentity indexed identity);
event IdentityRemoved(address indexed investorAddress, IIdentity indexed identity);
event IdentityUpdated(IIdentity indexed oldIdentity, IIdentity indexed newIdentity);
event CountryUpdated(address indexed investorAddress, uint16 indexed country);

event IdentityStored(address indexed investorAddress, IIdentity indexed identity);
event IdentityUnstored(address indexed investorAddress, IIdentity indexed identity);
event IdentityModified(IIdentity indexed oldIdentity, IIdentity indexed newIdentity);
event CountryModified(address indexed investorAddress, uint16 indexed country);
event IdentityRegistryBound(address indexed identityRegistry);
event IdentityRegistryUnbound(address indexed identityRegistry);

event TrustedIssuerAdded(IClaimIssuer indexed trustedIssuer, uint256[] claimTopics);
event TrustedIssuerRemoved(IClaimIssuer indexed trustedIssuer);
event ClaimTopicsUpdated(IClaimIssuer indexed trustedIssuer, uint256[] claimTopics);

event UpdatedTokenInformation(string indexed _newName,string indexed _newSymbol,uint8 _newDecimals,string _newVersion,address indexed _newOnchainID);
event IdentityRegistryAdded(address indexed _identityRegistry);
event ComplianceAdded(address indexed _compliance);
event RecoverySuccess(address indexed _lostWallet, address indexed _newWallet, address indexed _investorOnchainID);
event AddressFrozen(address indexed _userAddress, bool indexed _isFrozen, address indexed _owner);
event TokensFrozen(address indexed _userAddress, uint256 _amount);
event TokensUnfrozen(address indexed _userAddress, uint256 _amount);
event Paused(address _userAddress);
event Unpaused(address _userAddress);

event ClaimRevoked(bytes indexed signature);

event Approved(uint256 indexed executionId, bool approved);
event Executed(uint256 indexed executionId, address indexed to, uint256 indexed value, bytes data);
event ExecutionRequested(uint256 indexed executionId, address indexed to, uint256 indexed value, bytes data);
event ExecutionFailed(uint256 indexed executionId, address indexed to, uint256 indexed value, bytes data);
event KeyAdded(bytes32 indexed key, uint256 indexed purpose, uint256 indexed keyType);
event KeyRemoved(bytes32 indexed key, uint256 indexed purpose, uint256 indexed keyType);

event ClaimAdded(bytes32 indexed claimId,uint256 indexed topicId,uint256 scheme,address indexed issuer,bytes signature,bytes data,string uri);
event ClaimRemoved(bytes32 indexed claimId,uint256 indexed topicId,uint256 scheme,address indexed issuer,bytes signature,bytes data,string uri);
event ClaimChanged(bytes32 indexed claimId,uint256 indexed topicId,uint256 scheme,address indexed issuer,bytes signature,bytes data,string uri);

event UpdatedImplementation(address newAddress);

error IdentityAlreadyRegistered(bytes32 ccid, address account);
error IdentityNotFound(bytes32 ccid, address account);
error InvalidConfiguration(bytes errorReason);
error CredentialAlreadyRegistered(bytes32 ccid, bytes32 credentialTypeId);
error CredentialNotFound(bytes32 ccid, bytes32 credentialTypeId);
error InvalidConfiguration(string errorReason);
error RequirementExists(bytes32 requirementId);
error RequirementNotFound(bytes32 requirementId);
error SourceExists(bytes32 credentialTypeId, address identityRegistry, address credentialRegistry);
error SourceNotFound(bytes32 credentialTypeId, address identityRegistry, address credentialRegistry);
error UnsupportedDataSourceId(bytes32 dataSourceId);
error InvalidDataSourceConfiguration(bytes errorReason);
error TargetNotAttached(address target);
error TargetAlreadyAttached(address target);
error PolicyEngineUndefined();
error PolicyRunRejected(bytes4 selector, address policy);
error PolicyMapperError(address policy, bytes errorReason);
error PolicyRunError(bytes4 selector, address policy, bytes errorReason);
error PolicyRunUnauthorizedError(address account);
error PolicyPostRunError(bytes4 selector, address policy, bytes errorReason);
error UnsupportedSelector(bytes4 selector);
error ExtractorError(bytes4 selector, address extractor, bytes errorReason);

error Unauthorized();
"""

}

// Test for SolidityEvent parsing - tests the actual implementation
class SolidityEventSpec extends AnyFlatSpec with Matchers {

  "SolidityParser.parseEvents" should "parse simple events without parameters" in {
    val events = """
event SimpleEvent();
event AnotherEvent();
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "SimpleEvent()"
    parsed(1).sig shouldBe "AnotherEvent()"
  }

  it should "parse events with single parameters" in {
    val events = """
event AddedToSanctionsList(address indexed account);
event RemovedFromSanctionsList(address indexed account);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "AddedToSanctionsList(address)"
    parsed(1).sig shouldBe "RemovedFromSanctionsList(address)"
  }

  it should "parse events with multiple parameters" in {
    val events = """
event IdentityRegistered(bytes32 indexed ccid, address indexed account);
event CredentialRegistered(bytes32 indexed ccid, bytes32 indexed credentialTypeId, uint40 expiresAt, bytes credentialData);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "IdentityRegistered(bytes32,address)"
    parsed(1).sig shouldBe "CredentialRegistered(bytes32,bytes32,uint40,bytes)"
  }

  it should "parse events with array parameters" in {
    val events = """
event CredentialRequirementAdded(bytes32 indexed requirementId, bytes32[] credentialTypeIds, uint256 minValidations);
event TrustedIssuerAdded(IClaimIssuer indexed trustedIssuer, uint256[] claimTopics);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "CredentialRequirementAdded(bytes32,bytes32[],uint256)"
    parsed(1).sig shouldBe "TrustedIssuerAdded(IClaimIssuer,uint256[])"
  }

  it should "parse events with complex parameter types" in {
    val events = """
event UpdatedTokenInformation(string indexed _newName,string indexed _newSymbol,uint8 _newDecimals,string _newVersion,address indexed _newOnchainID);
event PolicyParametersSet(address indexed policy, bytes[] parameters);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "UpdatedTokenInformation(string,string,uint8,string,address)"
    parsed(1).sig shouldBe "PolicyParametersSet(address,bytes[])"
  }

  it should "parse events with custom types" in {
    val events = """
event IdentityRegistered(address indexed investorAddress, IIdentity indexed identity);
event ReserveMarginSet(ReserveMarginMode mode, uint256 amount);
event DefaultPolicyResultSet(PolicyResult defaultPolicy);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 3
    parsed(0).sig shouldBe "IdentityRegistered(address,IIdentity)"
    parsed(1).sig shouldBe "ReserveMarginSet(ReserveMarginMode,uint256)"
    parsed(2).sig shouldBe "DefaultPolicyResultSet(PolicyResult)"
  }

  it should "parse events with mixed indexed and non-indexed parameters" in {
    val events = """
event DataSourceSet(bytes32 indexed dataSourceId,address indexed source,bytes4 indexed schemaId,bytes metadata);
event OffchainDataRequested(bytes32 indexed dataSourceId, string endpoint);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "DataSourceSet(bytes32,address,bytes4,bytes)"
    parsed(1).sig shouldBe "OffchainDataRequested(bytes32,string)"
  }

  it should "parse events with no parameters" in {
    val events = """
event SimpleEventNoParams();
event AnotherEventNoParams();
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "SimpleEventNoParams()"
    parsed(1).sig shouldBe "AnotherEventNoParams()"
  }

  it should "ignore non-event lines" in {
    val events = """
event ValidEvent(address indexed account);

error InvalidConfiguration(bytes errorReason);

event AnotherValidEvent(uint256 value);
"""
    
    val parsed = SolidityParser.parseEvents(events)
    parsed should have size 2
    parsed(0).sig shouldBe "ValidEvent(address)"
    parsed(1).sig shouldBe "AnotherValidEvent(uint256)"
  }

  it should "handle empty input" in {
    val parsed = SolidityParser.parseEvents("")
    parsed should have size 0
  }

  it should "handle input with only whitespace" in {
    val parsed = SolidityParser.parseEvents("   \n  \t  \n  ")
    parsed should have size 0
  }

  it should "parse all events from EVENTS_DEFAULT" in {
    val ee = SolidityParser.parseEvents(TestEvents.EVENTS_DEFAULT)
    
    // Should have parsed events (not empty)
    ee should not be empty
    
    // Check some specific events
    val sigs = ee.map(_.sig).toSet
    
    sigs should contain("AddedToSanctionsList(address)")
    sigs should contain("IdentityRegistered(bytes32,address)")
    sigs should contain("CredentialRegistered(bytes32,bytes32,uint40,bytes)")
    sigs should contain("DataSourceSet(bytes32,address,bytes4,bytes)")
    sigs should contain("ActionCompleted(address)")
    sigs should contain("UpdatedTokenInformation(string,string,uint8,string,address)")
    
    // All events should have valid signatures (no parameter names or indexed keywords)
    sigs.foreach { sig =>
      sig should not include "indexed"
      sig should not include "account"
      sig should not include "ccid"
      sig should not include "credentialTypeId"
      sig should not include "expiresAt"
      sig should not include "credentialData"
    }

    info(s"sigs: ${ee}")
  }

  "SolidityEvent" should "generate consistent signatures" in {
    val event1 = new SolidityEvent("TestEvent(address,uint256)")
    val event2 = new SolidityEvent("TestEvent(address,uint256)")
    
    event1.sig shouldBe event2.sig
    event1.sig should not be empty
  }

  it should "generate different signatures for different events" in {
    val event1 = new SolidityEvent("TestEvent1(address)")
    val event2 = new SolidityEvent("TestEvent2(address)")
    
    event1.sig should not be event2.sig
  }

  it should "parse Transfer event and generate valid hex signature" in {
    val events = """
event Transfer(address indexed from, address indexed to, uint256 value);
event Transfer(address from, address to, uint256 amount);
event Transfer(address, address, uint256)
"""
    
    val p1 = SolidityParser.parseEvents(events)
    p1 should have size 3
    
    val e1 = p1(0)
    e1.sig shouldBe "Transfer(address,address,uint256)"        
    e1.sigHex shouldBe "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

    val e2 = p1(1)
    e2.sig shouldBe "Transfer(address,address,uint256)"        
    e2.sigHex shouldBe "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

    val e3 = p1(2)
    e3.sig shouldBe "Transfer(address,address,uint256)"        
    e3.sigHex shouldBe "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
  }

  "SolidityEvent name and types" should "extract name correctly" in {
    val event = new SolidityEvent("Transfer(address,address,uint256)")
    event.name shouldBe "Transfer"
  }

  it should "extract types correctly" in {
    val event = new SolidityEvent("Transfer(address,address,uint256)")
    event.types shouldBe "address,address,uint256"
  }

  it should "extract name from complex event" in {
    val event = new SolidityEvent("IdentityRegistered(bytes32,address)")
    event.name shouldBe "IdentityRegistered"
  }

  it should "extract types from complex event" in {
    val event = new SolidityEvent("IdentityRegistered(bytes32,address)")
    event.types shouldBe "bytes32,address"
  }

  it should "extract name from event with arrays" in {
    val event = new SolidityEvent("CredentialRequirementAdded(bytes32,bytes32[],uint256)")
    event.name shouldBe "CredentialRequirementAdded"
  }

  it should "extract types from event with arrays" in {
    val event = new SolidityEvent("CredentialRequirementAdded(bytes32,bytes32[],uint256)")
    event.types shouldBe "bytes32,bytes32[],uint256"
  }

  it should "extract name from event with custom types" in {
    val event = new SolidityEvent("IdentityRegistered(address,IIdentity)")
    event.name shouldBe "IdentityRegistered"
  }

  it should "extract types from event with custom types" in {
    val event = new SolidityEvent("IdentityRegistered(address,IIdentity)")
    event.types shouldBe "address,IIdentity"
  }

  it should "extract name from event with no parameters" in {
    val event = new SolidityEvent("SimpleEvent()")
    event.name shouldBe "SimpleEvent"
  }

  it should "extract types from event with no parameters" in {
    val event = new SolidityEvent("SimpleEvent()")
    event.types shouldBe ""
  }

  it should "extract name from event with complex nested types" in {
    val event = new SolidityEvent("ComplexEvent(bytes32[],address[2],uint256[][3])")
    event.name shouldBe "ComplexEvent"
  }

  it should "extract types from event with complex nested types" in {
    val event = new SolidityEvent("ComplexEvent(bytes32[],address[2],uint256[][3])")
    event.types shouldBe "bytes32[],address[2],uint256[][3]"
  }

  
}

// Test for SolidityError parsing - tests the actual implementation
class SolidityErrorSpec extends AnyFlatSpec with Matchers {

  "SolidityParser.parseErrors" should "parse simple errors without parameters" in {
    val errors = """
error Unauthorized();
error PolicyEngineUndefined();
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "Unauthorized()"
    parsed(1).sig shouldBe "PolicyEngineUndefined()"
  }

  it should "parse errors with single parameters" in {
    val errors = """
error IdentityAlreadyRegistered(bytes32 ccid, address account);
error IdentityNotFound(bytes32 ccid, address account);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "IdentityAlreadyRegistered(bytes32,address)"
    parsed(1).sig shouldBe "IdentityNotFound(bytes32,address)"
  }

  it should "parse errors with multiple parameters" in {
    val errors = """
error InvalidConfiguration(bytes errorReason);
error CredentialAlreadyRegistered(bytes32 ccid, bytes32 credentialTypeId);
error PolicyRunRejected(bytes4 selector, address policy);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 3
    parsed(0).sig shouldBe "InvalidConfiguration(bytes)"
    parsed(1).sig shouldBe "CredentialAlreadyRegistered(bytes32,bytes32)"
    parsed(2).sig shouldBe "PolicyRunRejected(bytes4,address)"
  }

  it should "parse errors with array parameters" in {
    val errors = """
error InvalidConfiguration(bytes[] errorReasons);
error PolicyRunError(bytes4 selector, address policy, bytes[] errorReasons);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "InvalidConfiguration(bytes[])"
    parsed(1).sig shouldBe "PolicyRunError(bytes4,address,bytes[])"
  }

  it should "parse errors with complex parameter types" in {
    val errors = """
error SourceExists(bytes32 credentialTypeId, address identityRegistry, address credentialRegistry);
error SourceNotFound(bytes32 credentialTypeId, address identityRegistry, address credentialRegistry);
error ExtractorError(bytes4 selector, address extractor, bytes errorReason);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 3
    parsed(0).sig shouldBe "SourceExists(bytes32,address,address)"
    parsed(1).sig shouldBe "SourceNotFound(bytes32,address,address)"
    parsed(2).sig shouldBe "ExtractorError(bytes4,address,bytes)"
  }

  it should "parse errors with custom types" in {
    val errors = """
error PolicyMapperError(address policy, bytes errorReason);
error PolicyRunUnauthorizedError(address account);
error UnsupportedSelector(bytes4 selector);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 3
    parsed(0).sig shouldBe "PolicyMapperError(address,bytes)"
    parsed(1).sig shouldBe "PolicyRunUnauthorizedError(address)"
    parsed(2).sig shouldBe "UnsupportedSelector(bytes4)"
  }

  it should "parse errors with string parameters" in {
    val errors = """
error InvalidConfiguration(string errorReason);
error OffchainDataRequested(string endpoint);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "InvalidConfiguration(string)"
    parsed(1).sig shouldBe "OffchainDataRequested(string)"
  }

  it should "parse errors with no parameters" in {
    val errors = """
error SimpleErrorNoParams();
error AnotherErrorNoParams();
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "SimpleErrorNoParams()"
    parsed(1).sig shouldBe "AnotherErrorNoParams()"
  }

  it should "ignore non-error lines" in {
    val errors = """
error ValidError(address indexed account);

event Transfer(address indexed from, address indexed to, uint256 value);

error AnotherValidError(uint256 value);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "ValidError(address)"
    parsed(1).sig shouldBe "AnotherValidError(uint256)"
  }

  it should "handle empty input" in {
    val parsed = SolidityParser.parseErrors("")
    parsed should have size 0
  }

  it should "handle input with only whitespace" in {
    val parsed = SolidityParser.parseErrors("   \n  \t  \n  ")
    parsed should have size 0
  }

  it should "parse all errors from EVENTS_DEFAULT" in {
    val ee = SolidityParser.parseErrors(TestEvents.EVENTS_DEFAULT)
    
    // Should have parsed errors (not empty)
    ee should not be empty
    
    // Check some specific errors
    val sigs = ee.map(_.sig).toSet
    
    sigs should contain("IdentityAlreadyRegistered(bytes32,address)")
    sigs should contain("IdentityNotFound(bytes32,address)")
    sigs should contain("InvalidConfiguration(bytes)")
    sigs should contain("InvalidConfiguration(string)")
    sigs should contain("Unauthorized()")
    sigs should contain("PolicyEngineUndefined()")
    sigs should contain("TargetNotAttached(address)")
    sigs should contain("PolicyRunRejected(bytes4,address)")
    
    // All errors should have valid signatures (no parameter names)
    sigs.foreach { sig =>
      sig should not include "ccid"
      sig should not include "account"
      sig should not include "errorReason"
      sig should not include "selector"
      sig should not include "policy"
    }

    info(s"error sigs: ${ee}")
  }

  "SolidityError" should "generate consistent signatures" in {
    val error1 = new SolidityError("TestError(address,uint256)")
    val error2 = new SolidityError("TestError(address,uint256)")
    
    error1.sig shouldBe error2.sig
    error1.sig should not be empty
  }

  it should "generate different signatures for different errors" in {
    val error1 = new SolidityError("TestError1(address)")
    val error2 = new SolidityError("TestError2(address)")
    
    error1.sig should not be error2.sig
  }

  it should "generate error signatures with 4-byte hex prefix" in {
    val errors = """
error Unauthorized();
error InvalidConfiguration(bytes errorReason);
error IdentityAlreadyRegistered(bytes32 ccid, address account);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 3
    
    val e1 = parsed(0)
    e1.sig shouldBe "Unauthorized()"
    e1.sigHex should have length 10 // "0x" + 8 hex chars = 10 chars
    
    val e2 = parsed(1)
    e2.sig shouldBe "InvalidConfiguration(bytes)"
    e2.sigHex should have length 10
    
    val e3 = parsed(2)
    e3.sig shouldBe "IdentityAlreadyRegistered(bytes32,address)"
    e3.sigHex should have length 10
    
    // All error signatures should start with "0x"
    parsed.foreach { error =>
      error.sigHex should startWith("0x")
    }
  }

  it should "generate different hex signatures for different error signatures" in {
    val error1 = new SolidityError("Unauthorized()")
    val error2 = new SolidityError("InvalidConfiguration(bytes)")
    
    error1.sigHex should not be error2.sigHex
    error1.sigHex should have length 10
    error2.sigHex should have length 10
  }

  it should "handle errors with complex nested types" in {
    val errors = """
error ComplexError(bytes32[] data, address[2] addresses, uint256[][3] nestedArrays);
error AnotherComplexError(mapping(address => uint256) balances, struct User user);
"""
    
    val parsed = SolidityParser.parseErrors(errors)
    parsed should have size 2
    parsed(0).sig shouldBe "ComplexError(bytes32[],address[2],uint256[][3])"
    parsed(1).sig shouldBe "AnotherComplexError(mapping(address => uint256),struct User)"
  }

  it should "decode error data PolicyRunRejected(bytes4,address) revert reason" in {
    val output = "0x1e55042ba9059cbb00000000000000000000000000000000000000000000000000000000000000000000000000000000ee8031f530845d8f72a54d8cc58f56dc86e9a56f"
    val errors = SolidityParser.parseErrors(TestEvents.EVENTS_DEFAULT)

    val d1 = SolidityError.decodeErrorData(errors,output)
    info(s"d1: ${d1}")
    d1 should be a Symbol("Success")
    d1.get shouldBe "PolicyRunRejected,0x1e55042b,0x00000000ee8031f530845d8f72a54d8cc58f56dc"
  }

  "SolidityError name and types" should "extract name correctly" in {
    val error = new SolidityError("Unauthorized()")
    error.name shouldBe "Unauthorized"
  }

  it should "extract types correctly" in {
    val error = new SolidityError("Unauthorized()")
    error.types shouldBe ""
  }

  it should "extract name from error with parameters" in {
    val error = new SolidityError("IdentityAlreadyRegistered(bytes32,address)")
    error.name shouldBe "IdentityAlreadyRegistered"
  }

  it should "extract types from error with parameters" in {
    val error = new SolidityError("IdentityAlreadyRegistered(bytes32,address)")
    error.types shouldBe "bytes32,address"
  }

  it should "extract name from error with arrays" in {
    val error = new SolidityError("InvalidConfiguration(bytes[])")
    error.name shouldBe "InvalidConfiguration"
  }

  it should "extract types from error with arrays" in {
    val error = new SolidityError("InvalidConfiguration(bytes[])")
    error.types shouldBe "bytes[]"
  }

  it should "extract name from error with complex types" in {
    val error = new SolidityError("PolicyRunError(bytes4,address,bytes)")
    error.name shouldBe "PolicyRunError"
  }

  it should "extract types from error with complex types" in {
    val error = new SolidityError("PolicyRunError(bytes4,address,bytes)")
    error.types shouldBe "bytes4,address,bytes"
  }

  it should "extract name from error with custom types" in {
    val error = new SolidityError("PolicyMapperError(address,bytes)")
    error.name shouldBe "PolicyMapperError"
  }

  it should "extract types from error with custom types" in {
    val error = new SolidityError("PolicyMapperError(address,bytes)")
    error.types shouldBe "address,bytes"
  }

  it should "extract name from error with string parameters" in {
    val error = new SolidityError("InvalidConfiguration(string)")
    error.name shouldBe "InvalidConfiguration"
  }

  it should "extract types from error with string parameters" in {
    val error = new SolidityError("InvalidConfiguration(string)")
    error.types shouldBe "string"
  }

  it should "extract name from error with multiple parameters" in {
    val error = new SolidityError("SourceExists(bytes32,address,address)")
    error.name shouldBe "SourceExists"
  }

  it should "extract types from error with multiple parameters" in {
    val error = new SolidityError("SourceExists(bytes32,address,address)")
    error.types shouldBe "bytes32,address,address"
  }

  it should "extract name from error with complex nested types" in {
    val error = new SolidityError("ComplexError(bytes32[],address[2],uint256[][3])")
    error.name shouldBe "ComplexError"
  }

  it should "extract types from error with complex nested types" in {
    val error = new SolidityError("ComplexError(bytes32[],address[2],uint256[][3])")
    error.types shouldBe "bytes32[],address[2],uint256[][3]"
  }

   
}

// Test for SolidityParser.parseFunctionsFromAbi
class SolidityFuncSpec extends AnyFlatSpec with Matchers {
  
  "SolidityParser.parseFunctionsFromAbi" should "parse functions from ERC20 ABI" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    funcs should not be empty
    info(s"Found ${funcs.size} functions")
    
    // Debug: print first few functions
    funcs.take(3).foreach(f => info(s"Function: name='${f.name}', types='${f.types}', sig='${f.sig}'"))
    
    // Test specific ERC20 functions by name
    val transferFunc = funcs.find(_.name == "transfer")
    transferFunc shouldBe defined
    transferFunc.get.name shouldBe "transfer"
    transferFunc.get.types shouldBe "address,uint256"
    transferFunc.get.sig shouldBe "transfer(address,uint256)"
    
    val allowanceFunc = funcs.find(_.name == "allowance")
    allowanceFunc shouldBe defined
    allowanceFunc.get.name shouldBe "allowance"
    allowanceFunc.get.types shouldBe "address,address"
    allowanceFunc.get.sig shouldBe "allowance(address,address)"
    
    val balanceOfFunc = funcs.find(_.name == "balanceOf")
    balanceOfFunc shouldBe defined
    balanceOfFunc.get.name shouldBe "balanceOf"
    balanceOfFunc.get.types shouldBe "address"
    balanceOfFunc.get.sig shouldBe "balanceOf(address)"
  }

  it should "parse functions with no parameters" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    val decimalsFunc = funcs.find(_.name == "decimals")
    decimalsFunc shouldBe defined
    decimalsFunc.get.name shouldBe "decimals"
    decimalsFunc.get.types shouldBe ""
    decimalsFunc.get.sig shouldBe "decimals()"
    
    val nameFunc = funcs.find(_.name == "name")
    nameFunc shouldBe defined
    nameFunc.get.name shouldBe "name"
    nameFunc.get.types shouldBe ""
    nameFunc.get.sig shouldBe "name()"
    
    val symbolFunc = funcs.find(_.name == "symbol")
    symbolFunc shouldBe defined
    symbolFunc.get.name shouldBe "symbol"
    symbolFunc.get.types shouldBe ""
    symbolFunc.get.sig shouldBe "symbol()"
    
    val totalSupplyFunc = funcs.find(_.name == "totalSupply")
    totalSupplyFunc shouldBe defined
    totalSupplyFunc.get.name shouldBe "totalSupply"
    totalSupplyFunc.get.types shouldBe ""
    totalSupplyFunc.get.sig shouldBe "totalSupply()"
  }

  it should "parse functions with single parameters" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    val balanceOfFunc = funcs.find(_.name == "balanceOf")
    balanceOfFunc shouldBe defined
    balanceOfFunc.get.name shouldBe "balanceOf"
    balanceOfFunc.get.types shouldBe "address"
    balanceOfFunc.get.sig shouldBe "balanceOf(address)"
    
    val ownerFunc = funcs.find(_.name == "owner")
    ownerFunc shouldBe defined
    ownerFunc.get.name shouldBe "owner"
    ownerFunc.get.types shouldBe ""
    ownerFunc.get.sig shouldBe "owner()"
    
    val versionFunc = funcs.find(_.name == "version")
    versionFunc shouldBe defined
    versionFunc.get.name shouldBe "version"
    versionFunc.get.types shouldBe ""
    versionFunc.get.sig shouldBe "version()"
  }

  it should "parse functions with multiple parameters" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    val approveFunc = funcs.find(_.name == "approve")
    approveFunc shouldBe defined
    approveFunc.get.name shouldBe "approve"
    approveFunc.get.types shouldBe "address,uint256"
    approveFunc.get.sig shouldBe "approve(address,uint256)"
    
    val mintFunc = funcs.find(_.name == "mint")
    mintFunc shouldBe defined
    mintFunc.get.name shouldBe "mint"
    mintFunc.get.types shouldBe "address,uint256"
    mintFunc.get.sig shouldBe "mint(address,uint256)"
    
    val transferOwnershipFunc = funcs.find(_.name == "transferOwnership")
    transferOwnershipFunc shouldBe defined
    transferOwnershipFunc.get.name shouldBe "transferOwnership"
    transferOwnershipFunc.get.types shouldBe "address"
    transferOwnershipFunc.get.sig shouldBe "transferOwnership(address)"
  }

  it should "generate correct function signatures" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    val transferFunc = funcs.find(_.name == "transfer")
    transferFunc shouldBe defined
    transferFunc.get.sig shouldBe "transfer(address,uint256)"
    
    val allowanceFunc = funcs.find(_.name == "allowance")
    allowanceFunc shouldBe defined
    allowanceFunc.get.sig shouldBe "allowance(address,address)"
    
    val decimalsFunc = funcs.find(_.name == "decimals")
    decimalsFunc shouldBe defined
    decimalsFunc.get.sig shouldBe "decimals()"
  }

  it should "generate correct function signature hashes" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
    
    val transferFunc = funcs.find(_.name == "transfer")
    transferFunc shouldBe defined
    transferFunc.get.sigHex shouldBe "0xa9059cbb" // Known ERC20 transfer function selector
    
    val allowanceFunc = funcs.find(_.name == "allowance")
    allowanceFunc shouldBe defined
    allowanceFunc.get.sigHex shouldBe "0xdd62ed3e" // Known ERC20 allowance function selector
    
    val balanceOfFunc = funcs.find(_.name == "balanceOf")
    balanceOfFunc shouldBe defined
    balanceOfFunc.get.sigHex shouldBe "0x70a08231" // Known ERC20 balanceOf function selector
  }

  it should "handle empty ABI gracefully" in {
    val emptyAbi = "[]"
    val funcs = SolidityParser.parseFunctionsFromAbi(emptyAbi)
    
    funcs shouldBe empty
  }

  it should "handle malformed ABI gracefully" in {
    val malformedAbi = """[{"type": "function", "name": "test"}]"""
    val funcs = SolidityParser.parseFunctionsFromAbi(malformedAbi)
    
    // Should not throw exception, but may return empty or partial results
    funcs should not be null
  }

  it should "parse all function types correctly" in {
    val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
    val funcs = SolidityParser.parseFunctionsFromAbi(abi)
 
    info(s"funcs: ${funcs}")
    
    // Verify we have the expected function names (not events or errors)
    val functionNames = funcs.map(_.name).toSet
    val expectedNames = Set(
      "allowance", "approve", "balanceOf", "decimals", "mint", 
      "name", "owner", "renounceOwnership", "symbol", "totalSupply", 
      "transfer", "transferFrom", "transferOwnership", "version"
    )
    
    // Check that we have the expected ERC20 function names
    functionNames should contain("transfer")
    functionNames should contain("allowance")
    functionNames should contain("balanceOf")
    functionNames should contain("approve")
    functionNames should contain("transferFrom")
    functionNames should contain("decimals")
    functionNames should contain("name")
    functionNames should contain("symbol")
    functionNames should contain("totalSupply")
    
    // Verify we don't have events (which should be filtered out)
    functionNames should not contain "Transfer"
    functionNames should not contain "Approval"
    functionNames should not contain "OwnershipTransferred"
  }

  // it should "maintain function signature consistency" in {
  //   val abi = scala.io.Source.fromResource("ABI_ERC20_1.json").mkString
  //   val funcs1 = SolidityParser.parseFunctionsFromAbi(abi)
  //   val funcs2 = SolidityParser.parseFunctionsFromAbi(abi)
    
  //   // Same ABI should produce identical results
  //   funcs1 should have size funcs2.size
    
  //   funcs1.zip(funcs2).foreach { case (f1, f2) =>
  //     f1.name shouldBe f2.name
  //     f1.types shouldBe f2.types
  //     f1.sig shouldBe f2.sig
  //     f1.sigHex shouldBe f2.sigHex
  //   }
  // }

}
