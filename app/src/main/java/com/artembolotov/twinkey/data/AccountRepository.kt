package com.artembolotov.twinkey.data

import com.artembolotov.twinkey.domain.Token

/**
 * Порт AccountsModel + AccountAction из iOS.
 *
 * CRUD-операции поверх KeychainService.
 * Список аккаунтов хранится в памяти между операциями и сбрасывается при каждом save.
 */
class AccountRepository(private val keychain: KeychainService) {

    // MARK: - Read

    fun loadAll(): List<Token> = keychain.loadAll()

    // MARK: - Write

    fun add(token: Token, current: List<Token>): List<Token> {
        val updated = current + token
        keychain.saveAll(updated)
        return updated
    }

    fun update(token: Token, current: List<Token>): List<Token> {
        val updated = current.map { if (it.id == token.id) token else it }
        keychain.saveAll(updated)
        return updated
    }

    fun delete(id: String, current: List<Token>): List<Token> {
        val updated = current.filter { it.id != id }
        keychain.saveAll(updated)
        return updated
    }

    fun move(fromIndex: Int, toIndex: Int, current: List<Token>): List<Token> {
        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        keychain.saveAll(mutable)
        return mutable
    }

    fun removeAll() {
        keychain.clear()
    }
}
