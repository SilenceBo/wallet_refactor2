package com.hunter.wallet.utils;

import com.hunter.wallet.entity.WalletInfo;
import com.hunter.wallet.exception.UnexpectedException;
import com.hunter.wallet.exception.VerifyFailException;
import com.hunter.wallet.exception.WalletLockedException;
import com.hunter.wallet.service.TxManageService;

import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Web3Sha3;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.web3j.crypto.WalletUtils.generateWalletFile;

public class Web3jUtil {

    private static final Logger logger = LoggerFactory.getLogger(Web3jUtil.class);
    public static final String URL = "https://mainnet.infura.io/c0oGHqQQlq6XJU2kz5DL";
    private static Web3j web3j;
    //public static final String URL = "https://rinkeby.infura.io/c0oGHqQQlq6XJU2kz5DL";

    /**
     * 返回与url的连接对象
     *
     * @return
     */
    public static Web3j getWeb3j() {
        if (null == web3j) {
            return Web3j.build(new HttpService(URL));
        } else {
            return web3j;
        }
    }

    /**
     * 查询指定地址的余额，返回余额
     *
     * @param address
     * @return
     * @throws IOException
     */
    public static String ethGetBalance(String address) throws IOException {
        Web3j web3 = Web3jUtil.getWeb3j();
        //查询指定地址的余额
        EthGetBalance ethGetBalance = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        BigInteger balance = BigInteger.ZERO;
        if (ethGetBalance.hasError()) {
            logger.error(ethGetBalance.getError().getMessage());
        } else {
            balance = ethGetBalance.getBalance();
        }
        //转换为Ether格式
        BigDecimal balanceEther = Convert.fromWei(balance.toString(), Convert.Unit.ETHER);
        logger.info(balanceEther.toString());
        return balanceEther.toString();
    }

    public static String newWallet(String password, File destinationDirectory)
            throws CipherException, IOException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        DeterministicSeed deterministicSeed = Web3jUtil.generatorSeed(password);
        String address = Web3jUtil.newWalletBySeed(deterministicSeed, password, destinationDirectory);
        return address;
    }

    /**
     * 生成助记词
     *
     * @param password
     * @return
     */
    public static DeterministicSeed generatorSeed(String password) {
        SecureRandom secureRandom = new SecureRandom();
        long creationTimeSeconds = System.currentTimeMillis() / 1000;
        DeterministicSeed deterministicSeed = new DeterministicSeed(secureRandom, 128, password, creationTimeSeconds);
        List<String> mnemonicCode = deterministicSeed.getMnemonicCode();
        return deterministicSeed;
    }

    /**
     * 根据助记词生成钱包
     *
     * @param seed
     * @return
     */
    public static String newWalletBySeed(DeterministicSeed seed, String password, File destinationDirectory)
            throws CipherException, IOException {
        DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(seed).build();
        List<ChildNumber> keyPath = HDUtils.parsePath("M/44H/60H/0H/0/0");
        DeterministicKey key = chain.getKeyByPath(keyPath, true);
        BigInteger privKey = key.getPrivKey();

        // Web3j
        Credentials credentials = Credentials.create(privKey.toString(16));
        String address = credentials.getAddress();
        String privateKey = privKey.toString(16);
        String path = generatorKeyStore(credentials, password, destinationDirectory);

        logger.info(address);
        logger.info(privateKey);
        logger.info(path);
        return address;
    }

    /**
     * 生成keystore文件
     *
     * @param credentials
     * @param password
     * @param destinationDirectory
     * @return
     * @throws CipherException
     * @throws IOException
     */
    public static String generatorKeyStore(Credentials credentials, String password, File destinationDirectory)
            throws CipherException, IOException {
        ECKeyPair ecKeyPair = credentials.getEcKeyPair();
        return generateWalletFile(password, ecKeyPair, destinationDirectory, true);
    }

    /**
     * 根据私钥得到钱包
     *
     * @param privateKey
     * @return
     * @throws IOException
     */
    public static String privateKeyToWallet(String privateKey) throws IOException {
        Web3j web3 = Web3jUtil.getWeb3j();
        ECKeyPair ecKeyPair = ECKeyPair.create(new BigInteger(privateKey, 16));
        Web3Sha3 web3Sha3 = web3.web3Sha3("0x" + ecKeyPair.getPublicKey().toString(16)).send();
        String addr = null;
        if (web3Sha3.hasError()) {
            logger.error(web3Sha3.getError().getMessage());
        } else {
            addr = web3Sha3.getResult().substring(26);
        }
        return "0x" + addr;
    }

    /**
     * 得到最近交易的gas均值
     *
     * @return
     * @throws IOException
     */
    public static BigInteger getGasPrice() throws IOException {
        Web3j web3 = Web3jUtil.getWeb3j();
        EthGasPrice ethGasPrice = web3.ethGasPrice().send();
        BigInteger gasPrice = null;
        if (ethGasPrice.hasError()) {
            logger.error(ethGasPrice.getError().getMessage());
        } else {
            gasPrice = ethGasPrice.getGasPrice();
        }
        return gasPrice;
    }

    /**
     * 获取交易估算的gas用量
     *
     * @param from
     * @param to
     * @return
     * @throws IOException
     */
    public static BigInteger getEstimateGas(String from, String to) throws IOException {
        Web3j web3 = Web3jUtil.getWeb3j();
        Transaction transaction = Transaction.createEthCallTransaction(from, to, null);
        EthEstimateGas ethEstimateGas = web3.ethEstimateGas(transaction).send();
        BigInteger estimateGas = null;
        if (ethEstimateGas.hasError()) {
            logger.error(ethEstimateGas.getError().getMessage());
        } else {
            estimateGas = ethEstimateGas.getAmountUsed();
        }
        return estimateGas;
    }

    /**
     * 转账操作
     *
     * @param from
     * @param to
     * @param gasPrice
     * @param gasLimit
     * @param value
     * @return transactionHash
     * @throws IOException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static String ethTransaction(String from, String to
            , BigInteger gasPrice, BigInteger gasLimit, String value, WalletInfo walletInfo, String password)
            throws WalletLockedException, UnexpectedException, VerifyFailException {
        Web3j web3 = Web3jUtil.getWeb3j();
        //证书
        //Credentials credentials = Credentials.create(privateKey);
        //交易证书
        EthGetTransactionCount ethGetTransactionCount = null;
        try {
            ethGetTransactionCount = web3.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        BigInteger nonce = null;
        if (ethGetTransactionCount.hasError()) {
            logger.error(ethGetTransactionCount.getError().getMessage());
        } else {
            nonce = ethGetTransactionCount.getTransactionCount();
        }
        //创建交易
        BigInteger ether = Convert.toWei(value, Convert.Unit.ETHER).toBigInteger();
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                nonce, gasPrice, gasLimit, to, ether);
        //签名Transaction，这里要对交易做签名
        // byte[] signedMessage = TransactionEncoder.signEthRawTransaction(rawTransaction, credentials);

        byte[] signedMessage = new byte[0];
        signedMessage = TxManageService.getInstance().signEthRawTransaction(walletInfo, password, rawTransaction);

        String hexValue = Numeric.toHexString(signedMessage);

        //发送交易
        EthSendTransaction ethSendTransaction =
                null;
        try {
            ethSendTransaction = web3.ethSendRawTransaction(hexValue).send();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String transactionHash = null;
        if (ethSendTransaction.hasError()) {
            logger.error(ethSendTransaction.getError().getMessage());
        } else {
            transactionHash = ethSendTransaction.getTransactionHash();
        }
        return transactionHash;
    }

    public static RawTransaction createEthTransferRawTransaction(String from, String to, BigInteger gasPrice, BigInteger gasLimit, BigInteger value)
            throws ExecutionException, InterruptedException {
        Web3j web3 = Web3jUtil.getWeb3j();
        EthGetTransactionCount ethGetTransactionCount = web3.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).sendAsync().get();
        if (ethGetTransactionCount.hasError()) {
            logger.error(ethGetTransactionCount.getError().getMessage());
            return null;
        } else {
            BigInteger nonce = ethGetTransactionCount.getTransactionCount();
            return RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value);
        }
    }

    public static RawTransaction createTokenTransferRawTransaction(String from, String to, String contractAddress, BigInteger gasPrice, BigInteger gasLimit, BigInteger value)
            throws ExecutionException, InterruptedException {
        EthGetTransactionCount ethGetTransactionCount = getWeb3j().ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).sendAsync().get();
        BigInteger nonce = ethGetTransactionCount.getTransactionCount();
        Uint256 _value = new Uint256(value);
        Address _to = new Address(to);
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReferenceExtend<Bool>() {
        });
        Function function = new Function("transfer", Arrays.asList(_to, _value), outputParameters);
        String encodedFunction = FunctionEncoder.encode(function);
        return RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, encodedFunction);
    }


    public static String sendTransaction(String signedMessage) throws IOException {
        Web3j web3 = Web3jUtil.getWeb3j();
        EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(signedMessage).send();
        if (ethSendTransaction.hasError()) {
            logger.error(ethSendTransaction.getError().getMessage());
            return null;
        } else {
            return ethSendTransaction.getTransactionHash();
        }
    }


}
