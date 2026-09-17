package com.k2767.course.login

import java.math.BigInteger

/**
 * 正方定制 CAS 统一身份认证平台 security.js（David Shapiro RSA）的密码加密实现。
 *
 * 多个正方定制 CAS 学校（浙江工业大学 oauth.zjut.edu.cn、河北传媒学院 cas.hebic.cn 等）
 * 使用同一套加密规格，此处作为唯一实现供各协议层共享：
 * 反转 UTF-16 码元序列 → 按 chunkSize 分块 → 块内每两个码元组成小端 16 位数字、
 * 数字按 2^16 进位组成大整数 → m^e mod n（无填充）→ 每块输出 16 进制，块间以空格连接。
 *
 * chunkSize = 2 × (模数的 16 位数字个数 - 1)，与密钥长度联动，必须动态计算。
 */
internal object ZhengfangRsaCrypto {
    class CryptoException(message: String) : Exception(message)

    fun encryptPassword(password: String, modulusHex: String, exponentHex: String): String {
        val modulus = bigIntFromHex(modulusHex) ?: throw CryptoException("CAS modulus is invalid")
        val exponent = bigIntFromHex(exponentHex) ?: throw CryptoException("CAS exponent is invalid")
        if (modulus.signum() <= 0 || exponent.signum() <= 0) {
            throw CryptoException("CAS public key is not positive")
        }
        val digitCount = (modulus.bitLength() + 15) / 16
        val chunkSize = 2 * (digitCount - 1)
        if (chunkSize <= 0) {
            throw CryptoException("CAS modulus is too small")
        }

        val units = password.reversed().map { it.code }.toMutableList()
        while (units.size % chunkSize != 0) units.add(0)

        val blocks = mutableListOf<String>()
        val digitsPerBlock = chunkSize / 2
        for (start in units.indices step chunkSize) {
            var value = BigInteger.ZERO
            for (j in (digitsPerBlock - 1) downTo 0) {
                val digit = units[start + 2 * j] + (units[start + 2 * j + 1] shl 8)
                value = value.shiftLeft(16).add(BigInteger.valueOf(digit.toLong()))
            }
            blocks.add(value.modPow(exponent, modulus).toString(RADIX_HEX))
        }
        return blocks.joinToString(" ")
    }

    private fun bigIntFromHex(hex: String): BigInteger? =
        hex.takeIf { it.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } }
            ?.let { BigInteger(it, RADIX_HEX) }

    private const val RADIX_HEX = 16
}
