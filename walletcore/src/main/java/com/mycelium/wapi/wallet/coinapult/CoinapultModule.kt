package com.mycelium.wapi.wallet.coinapult

import com.coinapult.api.httpclient.CoinapultClient
import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.manager.Config
import com.mycelium.wapi.wallet.manager.GenericModule
import com.mycelium.wapi.wallet.manager.WalletModule
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import java.util.*


class CoinapultModule(val accountKey: InMemoryPrivateKey,
                      val networkParameters: NetworkParameters,
                      val api: CoinapultApi,
                      val backing: WalletBacking<CoinapultAccountContext, CoinapultTransaction>,
                      val listener: AccountListener,
                      val metaDataStorage: IMetaDataStorage) : GenericModule(metaDataStorage), WalletModule {

    override fun setCurrencySettings(currencySettings: CurrencySettings) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val accounts = mutableMapOf<UUID, CoinapultAccount>()
    override fun getId(): String = ID

    override fun getAccounts(): List<WalletAccount<*, *>> = accounts.values.toList()

    override fun loadAccounts(): Map<UUID, WalletAccount<*, *>> {
        val result = mutableMapOf<UUID, WalletAccount<*, *>>()
        backing.loadAccountContexts().forEach { context ->
            val id = CoinapultUtils.getGuidForAsset(context.currency, accountKey.publicKey.publicKeyBytes)
            val account = CoinapultAccount(context, accountKey, api, backing.getAccountBacking(id)
                    , backing, networkParameters, context.currency, listener)
            result[account.id] = account
        }
        return result
    }

    override fun createAccount(config: Config): WalletAccount<*, *> {
        if (config is CoinapultConfig) {
            val id = CoinapultUtils.getGuidForAsset(config.currency, accountKey.publicKey.publicKeyBytes)
            try {
                api.activate(config.mail)
                val address = api.getAddress(config.currency, null)
                if (address != null) {
                    val context = CoinapultAccountContext(id, address, false, config.currency)
                    backing.createAccountContext(context)
                    val result = CoinapultAccount(context, accountKey, api, backing.getAccountBacking(id)
                            , backing, networkParameters, config.currency, listener)
                    accounts[result.id] = result
                    val baseLabel = "Coinapult ${config.currency.name}"
                    result.label = createLabel(baseLabel, result.id)
                    return result
                }
            } catch (e: CoinapultClient.CoinapultBackendException) {
                throw IllegalStateException("Account can't be created. Problem with endpoint", e)
            }
        }
        throw IllegalStateException("Account can't be created")
    }

    override fun canCreateAccount(config: Config): Boolean {
        return config is CoinapultConfig
    }

    override fun deleteAccount(walletAccount: WalletAccount<*, *>, keyCipher: KeyCipher): Boolean {
        backing.deleteAccountContext(walletAccount.id)
        return true
    }

    fun setMail(mail: String): Boolean {
        return api.setMail(mail)
    }

    fun verifyMail(link: String, email: String): Boolean {
        return api.verifyMail(link, email)
    }

    companion object {
        @JvmField
        val ID: String = "coinapult module"
    }
}

/**
 * Get active coinapult accounts
 *
 * @return list of accounts
 */
fun WalletManager.getCoinapultAccounts(): List<WalletAccount<*, *>> = getAccounts().filter { it is CoinapultAccount && it.isVisible && it.isActive }

/**
 * Get coinapult account by coin type
 *
 * @return list of accounts
 */
fun WalletManager.getCoinapultAccount(currency: Currency): WalletAccount<*, *>? = getCoinapultAccounts().find { it.coinType == currency }
