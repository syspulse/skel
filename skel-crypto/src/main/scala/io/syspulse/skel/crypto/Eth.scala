package io.syspulse.skel.crypto

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import scala.jdk.FutureConverters._
import os._
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters
import java.math.BigInteger

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey

import java.security.Security

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.key
import io.syspulse.skel.crypto.eth.abi3.Abi
import java.io.File

import java.nio.charset.StandardCharsets

import org.web3j.crypto.{ECKeyPair,ECDSASignature,Sign,Credentials,WalletUtils,Bip32ECKeyPair,MnemonicUtils,Keys,RawTransaction,TransactionEncoder}
import org.web3j.utils.{Numeric}
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.crypto
import org.web3j.utils.Convert
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.TransactionManager
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.response.TransactionReceiptProcessor
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.abi.datatypes
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.abi.FunctionReturnDecoder
import io.syspulse.skel.crypto.eth.Solidity
import java.security.SecureRandom

object Eth {
  val log = Logger(s"${this}")

  val rnd0 = new SecureRandom()
  import key._
  
  def presig(m:String):Array[Byte] = presig(m.getBytes())
  def presig(m:Array[Byte]):Array[Byte] = {
    val p = "\u0019Ethereum Signed Message:\n" + m.size; 
    Hash.keccak256((Numeric.hexStringToByteArray(p) ++ m).toArray)
  }
  
  def normalize(b0:Array[Byte],sz:Int):Array[Byte] = {
    val b1:Array[Byte] = b0.size match {
      case _ if(b0.size == sz -1) => b0.toArray.+:(0)
      case `sz` => b0
      case _ if(b0.size == sz + 1)  => b0.drop(1)
      case _ => Array.fill(sz - b0.size)(0.toByte) ++ b0
    }
    b1
  }

  def denormalize(sk:SK,pk:PK): ECKeyPair = {
    val sk1:SK = if(sk(0) < 0) Array[Byte](0) ++ sk else sk
    val pk1:PK = if(pk(0) < 0) Array[Byte](0) ++ pk else pk
    new ECKeyPair(new BigInteger(sk1),new BigInteger(pk1)) 
  }

  def normalize(kk:ECKeyPair):(SK,PK) = {
    val skb = kk.getPrivateKey().toByteArray
    val sk:Array[Byte] = skb.size match {
      case 31 => skb.toArray.+:(0)
      case 32 => skb
      case 33 => skb.drop(1)
      case _ => Array.fill(32 - skb.size)(0.toByte) ++ skb
    }
    val pkb = kk.getPublicKey().toByteArray
    val pk:Array[Byte] = pkb.size match {
      case 63 => pkb.toArray.+:(0)
      case 64 => pkb
      case 65 => pkb.drop(1)
      case _ => Array.fill(64 - pkb.size)(0.toByte) ++ pkb
    }
    (sk,pk) 
  }

  def generate(sk:Array[Byte]):Try[KeyPair] = { 
    val kk = ECKeyPair.create(sk)
    val nkk = normalize(kk)
    Success(KeyECDSA(nkk._1,nkk._2))
  }
  
  def generate(sk:String):Try[KeyPair] = { 
    generate(Numeric.hexStringToByteArray(sk))    
  }

  // generate random
  def generateRandom():Try[KeyPair] = { 
    val kk = Keys.createEcKeyPair(); 
    val nkk = normalize(kk)
    Success(KeyECDSA(nkk._1,nkk._2))
  }

  // BouncyCastle has a problem with random keys in tests  
  def random():Try[KeyPair] = { 
    val sk = Array.fill(32)(rnd0.nextInt(256).toByte)
    generate(sk)
  }

  // derive new Secure Keys from PrivateKey
  def deriveKey(sk:String, msg:String, nonce:Long = -1L):String = {
    val sig = sign(msg,(if(nonce == -1L) msg else s"${msg}:${nonce}"))
    Util.sha256(sig)
  }

  def address(pk:String):String = address(Numeric.hexStringToByteArray(pk))
  def address(pk:PK):String = Util.hex(Hash.keccak256(pk).takeRight(20))

  // compatible with OpenSSL signature encoding
  def fromSig(rs:String):(String,String) = { 
    if(rs==null || rs.trim.isBlank || !rs.contains(":")) return ("","")
    val (r,s) = rs.split(":").toList match { 
      case r::s::Nil => (r,s)
      case r::s::_ => (r,s)
      case _ => ("","")
    }
    (r,s)
  }

  def toSig(sig: ECDSASignature):String = s"${Util.hex(sig.r.toByteArray)}:${Util.hex(sig.s.toByteArray)}"

  def sign(m:String,sk:String):String = {
    if(m==null) return ""
    sign(m.getBytes(),Numeric.hexStringToByteArray(sk))
  }

  def sign(m:String,sk:SK):String = {
    if(m==null) return ""
    sign(m.getBytes(),sk)
  }

  def sign(m:Array[Byte],sk:SK):String = {
    if(m==null || sk==null) return ""
    
    val kk = ECKeyPair.create(sk)
    toSig(kk.sign(presig(m)))
  }

  def verify(m:String,sig:String,pk:String):Boolean = verify(m.getBytes(),sig,pk)
  def verify(m:String,sig:String,pk:PK):Boolean = verify(m.getBytes(),sig,Util.hex(pk))
  def verify(m:Array[Byte],sig:String,pk:PK):Boolean = verify(m,sig,Util.hex(pk))  
  
  def verify(m:Array[Byte],sig:String,pk:String):Boolean = verifyAddress(m,sig,address(pk))  
  def verifyAddress(m:String,sig:String,addr:String):Boolean = verifyAddress(m.getBytes(),sig,addr)

  def verifyAddress(m:Array[Byte],sig:String,addr0:String):Boolean = {
    if(m==null || sig==null || sig.isEmpty || addr0==null || addr0.isEmpty ) return false

    val addr = addr0.toLowerCase
    val rs = Eth.fromSig(sig)
    try {
        val signature = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray(rs._1)),new BigInteger(Numeric.hexStringToByteArray(rs._2)))
        val h = presig(m)
        val r1 = Sign.recoverFromSignature(0,signature,h)
        val r2 = Sign.recoverFromSignature(1,signature,h)
      
        //The right way is probably to migrate to signed BigInteger(1,r1.toByteArray)
        address(normalize(r1.toByteArray,64)) == addr || address(normalize(r2.toByteArray,64)) == addr
    } catch {
      case e:Exception => false
    }
  }

  // return (SK,PK)
  def readKeystore(keystorePass:String,keystoreFile:String):Try[KeyPair] = {
    try {
      val c = WalletUtils.loadCredentials(keystorePass, keystoreFile)
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success(KeyECDSA(c.getEcKeyPair().getPrivateKey().toByteArray,c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64)))
    }catch {
      case e:Exception => Failure(e)
    }
  }

  // some "magic" to move generated file
  def writeKeystore(sk:SK,pk:PK,keystorePass:String,keystoreFile:String):Try[String] = {
    try {
      val f = new File(keystoreFile)
      val dir = Option(f.getParent()).getOrElse("./")
      val file = f.getName()
      val generatedFileName = 
        WalletUtils.generateWalletFile(keystorePass,Eth.denormalize(sk,pk),new File(dir),! keystorePass.isBlank)
      
      f.delete()
      val newFile = dir + "/" + generatedFileName
      os.move(Path(newFile,os.pwd),Path(keystoreFile,os.pwd),true)

      Success(Path(keystoreFile,os.pwd).toString())

    }catch {
      case e:Exception => Failure(e)
    }
  }

  def generateFromMnemo(mnemonic:String,mnemoPass:String = null):Try[KeyPair] = {
    try {
      val c = WalletUtils.loadBip39Credentials(mnemoPass, mnemonic)
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success(KeyECDSA(c.getEcKeyPair().getPrivateKey().toByteArray,c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64)))
    }catch {
      case e:Exception => Failure(e)
    }
  }

  def generateFromMnemoMetamask(mnemonic:String):Try[KeyPair] = 
    generateFromMnemoPath(mnemonic,"m/44'/60'/0'/0")

  def generateFromMnemoPath(mnemonic:String,derivation:String, mnemoPass:String = null):Try[KeyPair] = {
    // def   m/44'/60'/0'/1
    //       m/44'/60'/0'/0
    val ss = derivation.split("/")
    
    if(ss.size < 2) return Failure(new Exception(s"invalid derivation path: '${derivation}'"))
    if(ss(0) != "m") return Failure(new Exception(s"invalid derivation path start: '${derivation}'"))

    val derivationPath = ss.tail.foldLeft(Array[Int]())( (path,part) => {
      val bits = 
        if(part.endsWith("'")) 
          part.stripSuffix("'").toInt | Bip32ECKeyPair.HARDENED_BIT 
        else 
          part.toInt
      path :+ bits
    }) :+ 0

    //println(s"${derivationPath.toList}")
    //val derivationPath = Seq(44 | Bip32ECKeyPair.HARDENED_BIT, 60 | Bip32ECKeyPair.HARDENED_BIT, 0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0).toArray
    //println(s"${Seq(44 | Bip32ECKeyPair.HARDENED_BIT, 60 | Bip32ECKeyPair.HARDENED_BIT, 0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0).toList}")

    try {
      val master = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(mnemonic, mnemoPass));
      val  derived = Bip32ECKeyPair.deriveKeyPair(master, derivationPath);

      val c = Credentials.create(derived)
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success(KeyECDSA(c.getEcKeyPair().getPrivateKey().toByteArray,c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64)))
    }
    catch {
      case e:Exception => Failure(e)
    }
  }

  def parseMetamaskSignatureData(sig:Array[Byte]):SignatureEth = {
    var v = sig(64)
    if (v < 27) {
      v = (v.toInt + 27).toByte
    }

    val r = sig.take(32)
    val s = sig.drop(32).take(32)
    //new Sign.SignatureData(v, r, s)
    SignatureEth(r,s,v)
  }
  
  def signMetamask(msg:String,kp:KeyPair) = {
    val ecKP = ECKeyPair.create(kp.sk)
    val sig:Sign.SignatureData = Sign.signPrefixedMessage(msg.getBytes(), ecKP);
    SignatureEth(sig.getR(),sig.getS(),sig.getV())
  }

  def recoverMetamask(msg:String,sigData:Array[Byte]):Try[PK] = {
    recoverMetamask(msg,parseMetamaskSignatureData(sigData))
  }

  def recoverMetamask(msg:String,sig:SignatureEth):Try[PK] = {
    val sigData:Sign.SignatureData = new Sign.SignatureData(sig.getV(),sig.r,sig.s)
    val key = Sign.signedPrefixedMessageToKey(msg.getBytes(),sigData)
    Success(Eth.normalize(key.toByteArray(),64))
  }

  // =============================================================================== Wallet ========

  def web3(rpcUri:String = "http://localhost:8545") = {
    Web3j.build(new HttpService(rpcUri))
  }

  def percentageToWei(v:BigInt,percentage:String):Double = {
    val current = v.toDouble
    val gasNew = if(percentage.trim.startsWith("+") || percentage.trim.startsWith("-")) {
      current + current * (percentage.trim.stripSuffix("%").toDouble / 100.0 )
    } else {
      current * (percentage.trim.stripSuffix("%").toDouble / 100.0 )
    }
    if(gasNew < 0.0)
      0.0
    else
      gasNew
  }

  def strToWei(v:String)(implicit web3:Web3j):Try[BigInt] = {
    v.trim.toLowerCase.split("\\s+").toList match {
      case "" :: Nil =>
        Success(0)
        
      case ("current" | "market") :: Nil =>
        getGasPrice()(web3) match {
          case Success(v) => Success(v)
          case f => f
        }
      // percentage based from current
      case percentage :: Nil if(percentage.trim.endsWith("%"))=> 
        getGasPrice()(web3) match {
          case Success(v) => 
            val gasNew = percentageToWei(v,percentage)
            Success(BigDecimal.valueOf(gasNew).toBigInt)
          case f => f
        }
      case v :: "eth" :: _ => 
        Success(Convert.toWei(v,Convert.Unit.ETHER).toBigInteger)
      case v :: unit :: _ => 
        val u = org.web3j.utils.Convert.Unit.fromString(unit)
        Success(Convert.toWei(v,u).toBigInteger)
      case v :: Nil =>
        Success(Convert.toWei(v,Convert.Unit.WEI).toBigInteger)
      case _ =>
        Success(Convert.toWei(v,Convert.Unit.WEI).toBigInteger)
    }
  }

  // ATTENTION !!!
  // gas is in GWEI 
  // value is in ETHER   
  // 11155111 - Sepolia
  def signTx(sk:String, to:String, value:String, nonce:Long = 0, 
             gasPrice:String = "20.0", gasTip:String = "5.0", gasLimit:Long = 21000,
             data:Option[String] = None,
             chainId:Long = 11155111)(implicit web3:Web3j):Try[String] = 
    for {
      nonceTx <- if(nonce == -1 ) getNonce(Credentials.create(sk).getAddress()) else Success(nonce)
      value <- strToWei(value)
      gasPrice <- strToWei(gasPrice)
      gasTip <- strToWei(gasTip)  
      hash <- signTransaction(sk,
             to, 
             value,
             nonceTx, 
             gasPrice, 
             gasTip,
             gasLimit,
             data,
             chainId)
    } yield hash
  
  def signTransaction(sk:String, to:String, value:BigInt, nonce:Long, 
             gasPrice:BigInt, gasTip:BigInt, gasLimit:Long = 21000,
             data:Option[String] = None,
             chainId:Long = 11155111):Try[String] = {
    
    val rawTx: RawTransaction = if(data.isDefined)
      RawTransaction.createTransaction(
        chainId,
        BigInteger.valueOf(nonce), 
        BigInteger.valueOf(gasLimit), 
        to,            
        value.bigInteger,
        data.get,
        gasTip.bigInteger,
        gasPrice.bigInteger
      )
    else 
      RawTransaction.createEtherTransaction(
        chainId,
        BigInteger.valueOf(nonce), 
        BigInteger.valueOf(gasLimit),
        to,            
        value.bigInteger,
        gasTip.bigInteger,
        gasPrice.bigInteger
      )
    
    val signedMessage = TransactionEncoder.signMessage(
      rawTx, 
      chainId, 
      Credentials.create(sk)
    )

    Success(Numeric.toHexString(signedMessage))
  }

  def transaction(sk:String, to:String, value:String, 
             gasPrice:String = "20.0", gasTip:String = "5.0", gasLimit:Long = 21000,
             data:Option[String] = None, nonce:Long = -1,
             chainId:Long = 11155111)(implicit web3:Web3j):Try[TransactionReceipt] = {
    
    val cred: Credentials = Credentials.create(sk)

    for {
      nonceTx <- if(nonce == -1 ) getNonce(cred.getAddress()) else Success(nonce)
      valueWei <- strToWei(value)
      gasPriceWei <- strToWei(gasPrice)
      gasTipWei <- strToWei(gasTip)
      sig <- signTransaction(sk,
             to, 
             valueWei,
             nonceTx, 
             gasPriceWei, 
             gasTipWei,
             gasLimit,
             data,
             chainId)
      r <- transaction(sig)
    } yield r
  }

  // Raw transaction (Blocking until hash is returned !)
  def transaction(sig:String)(implicit web3:Web3j):Try[TransactionReceipt] = {
    // val ver = web3.web3ClientVersion().send()
    // val id = web3.netVersion().send().getNetVersion()
    // log.info(s"web3: ${ver.getWeb3ClientVersion()}/${id}")    
        
    for {
      r <- Success(web3.ethSendRawTransaction(sig).send())
      
      txHash <- {    
        val txHash = r.getTransactionHash()

        if(txHash == null) {
          //log.error(s"Tx[${to},${valueWei},${gasPriceWei}/${gasTipWei}]: ${r.getError().getMessage()}")
          throw new Exception(r.getError().getMessage())
        } 
        
        log.info(s"txHash: ${txHash}")
        Success(txHash)
      }
      
      txReceipt <- {
        val receiptProcessor:TransactionReceiptProcessor = new PollingTransactionReceiptProcessor(
          web3, 
          TransactionManager.DEFAULT_POLLING_FREQUENCY, 
          TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH
        )
        
        val txReceipt:TransactionReceipt = receiptProcessor.waitForTransactionReceipt(txHash)
        log.info(s"txReceipt: ${txReceipt}")
        
        if(txReceipt.getStatus() != "0x1") {
          new Exception(s"${txHash}: failed with status=${txReceipt.getStatus()}")
        } 
        Success(txReceipt)
      }
    } yield txReceipt
    
  }

  def send(sig:String)(implicit web3:Web3j):Try[String] = {
    for {
      r <- Success(web3.ethSendRawTransaction(sig).send())
      
      txHash <- {    
        val txHash = r.getTransactionHash()

        if(txHash == null) {
          //log.error(s"Tx[${to},${valueWei},${gasPriceWei}/${gasTipWei}]: ${r.getError().getMessage()}")
          throw new Exception(r.getError().getMessage())
        } 
        
        log.info(s"txHash: ${txHash}")
        Success(txHash)
      }      
    } yield txHash
  }

  // -----------------------------------------------------------------------------
  def cotract(sk:String, contract:String, data:String,  value:String,
             gasPrice:String, gasTip:String, gasLimit:Long = 21000,             
             chainId:Long = 11155111, rpcUri:String = "http://localhost:8545") = 
    sendContract(sk,
             contract, data,
             Convert.toWei(value,Convert.Unit.ETHER).toBigInteger(),
             Convert.toWei(gasPrice,Convert.Unit.GWEI).toBigInteger(), 
             Convert.toWei(gasTip,Convert.Unit.GWEI).toBigInteger(),
             gasLimit,
             chainId,
             rpcUri) 


  def sendContract(sk:String, contract:String, data:String,value:BigInt,
             gasPrice:BigInt, gasTip:BigInt, gasLimit:Long = 21000,             
             chainId:Long = 11155111, rpcUri:String = "http://localhost:8545"):Try[TransactionReceipt] = {
    
    val web3 = Eth.web3(rpcUri)
    val ver = web3.web3ClientVersion().send()
    val id = web3.netVersion().send().getNetVersion();
    log.info(s"web3: ${ver}/${id}")

    val cred: Credentials = Credentials.create(sk)
    
    val txManager:TransactionManager = new RawTransactionManager(web3, cred, chainId)

    val r = try {
      txManager.sendEIP1559Transaction(
        chainId,
        gasTip.bigInteger,
        gasPrice.bigInteger,
        BigInteger.valueOf(gasLimit),
        contract,
        data,
        value.bigInteger,
        true
      )
    } catch {
      case e:Exception =>
        return Failure(e)
    }

    val txHash = r.getTransactionHash()

    if(txHash == null) {
      log.error(s"Tx[${contract},${value},${gasPrice}/${gasTip}]: ${r.getError().getMessage()}")
      return(Failure(new Exception(r.getError().getMessage())))
    }

    log.info(s"txHash: ${txHash}")
    
    val receiptProcessor:TransactionReceiptProcessor = new PollingTransactionReceiptProcessor(
      web3, 
      TransactionManager.DEFAULT_POLLING_FREQUENCY, 
      TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH
    )
    
    val txReceipt:TransactionReceipt = receiptProcessor.waitForTransactionReceipt(txHash)
    if(txReceipt.getStatus() != "0x1") {
      return Failure(new Exception(s"${txHash}: failed with status=${txReceipt.getStatus()}"))
    }
    Success(txReceipt)
  }
  
  def getGasPrice(rpcUri:String = "http://localhost:8545"):Try[BigInt] = { 
    getGasPrice()(Eth.web3(rpcUri))
  }

  def getGasPrice()(implicit web3:Web3j):Try[BigInt] = {     
    // val ver = web3.web3ClientVersion().send()
    // val id = web3.netVersion().send().getNetVersion();
    // log.info(s"web3: ${ver}/${id}")
    
    val r = try {
      web3.ethGasPrice().send()
    } catch {
      case e:Exception => return Failure(e)
    }

    r.hasError() match {
      case false => 
        Success(r.getGasPrice().longValue())
      case true =>
        Failure(new Exception(r.getResult()))
    }
  }

  def getNonce(addr:String)(implicit web3:Web3j):Try[Long] = {
    val nonce = try {
      Success(
        web3.ethGetTransactionCount(addr,DefaultBlockParameterName.PENDING).send().getTransactionCount().longValue()
      )
    } catch {
      case e:Exception => Failure(e)
    }
    nonce
  }

  def getBalance(addr:String)(implicit web3:Web3j):Try[BigInt] = {
    val bal = try {
      Success(
        BigInt(web3.ethGetBalance(addr,DefaultBlockParameterName.PENDING).send().getBalance())
      )
    } catch {
      case e:Exception => Failure(e)
    }
    bal
  }

  def getBalanceAsync(addr:String)(implicit web3:Web3j,ec: ExecutionContext):Future[BigInt] = {
    web3
      .ethGetBalance(addr,DefaultBlockParameterName.PENDING).sendAsync()
      .asScala
      .map(r => BigInt(r.getBalance()))
  }

  // input is calldata and must be already encoded
  def estimateGas(from:String,contractAddress:String,input:String)(implicit web3:Web3j):Try[BigInt] = {
    val tx = Transaction.createEthCallTransaction(from, contractAddress, input)

    for {
      r <- Success(web3.ethEstimateGas(tx).send())
      
      cost <- {
        if(r.hasError()) {
          throw new Exception(r.getError().getMessage())
        }

        val cost = r.getAmountUsed()

        if(cost == null) {
          //log.error(s"Tx[${to},${valueWei},${gasPriceWei}/${gasTipWei}]: ${r.getError().getMessage()}")
          throw new Exception(r.getError().getMessage())
        } 
        
        log.info(s"${contractAddress}: ${input}: cost=${cost}")
        Success(cost)
      }      
    } yield cost
  }

  //
  def estimateFunc(
    from:String,
    contractAddress:String,
    func:String,
    abiDef:String,
    inputs:Seq[Any]    
    )(implicit web3:Web3j):Try[BigInt] = {

    val abi = Abi.parse(abiDef)
    val inputsParsed:Seq[datatypes.Type[_]] = abi.getInputs(func,inputs)
    val outputsParsed:Seq[TypeReference[_]] = abi.getOutputs(func)

    estimateFunc(from,contractAddress,func,inputsParsed,outputsParsed)
  }

  protected def estimateFunc(
    from:String,
    contractAddress:String,
    func:String,
    inputs:Seq[datatypes.Type[_]],
    outputs:Seq[TypeReference[_]]
    )(implicit web3:Web3j):Try[BigInt] = {

    // val inputParameters = List[datatypes.Type[_]]().asJava
    // val outputParameters = List[TypeReference[_]](new TypeReference[datatypes.generated.Uint256](){}).asJava
    val inputParameters = inputs.asJava
    val outputParameters = outputs.asJava
    val function = new datatypes.Function(func, inputParameters , outputParameters)
    val encodedFunction = FunctionEncoder.encode(function)

    estimateGas(from,contractAddress,encodedFunction)    
  }

  def call(from:String,contractAddress:String,inputData:String,outputType:Option[String] = None)(implicit web3:Web3j):Try[String] = {
    val tx = Transaction.createEthCallTransaction(from, contractAddress, inputData)

    for {
      r <- Success(web3.ethCall(tx,DefaultBlockParameter.valueOf("latest")).send())
      
      result <- {
        if(r.hasError()) {
          throw new Exception(r.getError().getMessage())
        }

        if(r.isReverted()) {
          throw new Exception(s"reverted: ${r.getRevertReason()}")
        }

        val result = r.getValue()

        if(result == null) {
          //log.error(s"Tx[${to},${valueWei},${gasPriceWei}/${gasTipWei}]: ${r.getError().getMessage()}")
          throw new Exception(s"${contractAddress}: data=${inputData}: result=${result}")
        } 
        
        log.info(s"call: ${contractAddress}: data=${inputData}: result=${result} (outputType=${outputType})")
        
        if(outputType.isDefined && ! outputType.get.isEmpty()) {
          Solidity.decodeResult(result,outputType.get)
        } else {
          Success(result)
        }
      }      
    } yield result
  }

  def hexToUtf8String(hex: String): String = {    
    val cleanHex = hex.stripPrefix("0x")
    // no data
    if(cleanHex.isEmpty) return ""

    val bytes = cleanHex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)    
    val ref = Seq(new TypeReference[datatypes.Utf8String]() {})
      .toList
      .asInstanceOf[List[TypeReference[datatypes.Type[_]]]]
      .asJava
    
    val decodedList = FunctionReturnDecoder.decode(hex,ref)
    
    // Extract the string value
    decodedList.get(0).getValue.asInstanceOf[String]
  }
    
  def estimate(from:String,contractAddress:String,func:String,params:Seq[String])(implicit web3:Web3j):Try[BigInt] = {
    val (encodedFunction,outputType) = encodeFunction(func,params)
    estimateGas(from,contractAddress,encodedFunction)
  }

  def call(from:String,contractAddress:String,func:String,params:Seq[String])(implicit web3:Web3j):Try[String] = {
    val (encodedFunction,outputType) = encodeFunction(func,params)
    for {
      r <- call(from,contractAddress,encodedFunction,outputType = Some(outputType))
      //r <- Solidity.decodeResult(r,outputType)
    } yield r
  }

  def callWithParams(from:String,contractAddress:String,func:String,params:String)(implicit web3:Web3j):Try[String] = {
    call(from,contractAddress,func,if(params.isEmpty) Seq.empty else params.split("\\s+").toSeq)
  }
    
  def encodeFunction(func:String,params:Seq[String]):(String,String) = {
    val (encodedFunction,outputType) = Solidity.encodeFunctionWithOutputType(func,params)    
    (encodedFunction,outputType)
  }

  def encodeFunctionWithParams(func:String,params:String):(String,String) = {
    encodeFunction(func,if(params.isEmpty) Seq.empty else params.split("\\s+").toSeq)
  }

  def getBalanceToken(addr:String,tokens:Seq[String])(implicit web3:Web3j):Seq[Try[BigInt]] = {
    val balances = tokens.map(token => {
      try {
        val r = call(addr,token,"balanceOf(address)(uint256)",Seq(addr))        
        r.map(b => {
          BigInt(b)
        })        
      } catch {
        case e:Exception =>           
          Failure(e)
      }
    })
    balances
  }
}