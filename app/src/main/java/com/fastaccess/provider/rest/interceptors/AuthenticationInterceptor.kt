package com.fastaccess.provider.rest.interceptors

import com.fastaccess.exception.AuthNullOrBlankException
import com.fastaccess.helper.PrefGetter
import com.fastaccess.provider.scheme.LinkParserHelper
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthenticationInterceptor : Interceptor {
    private var isScrapping: Boolean = false
    private var token: String? = null
    private var otp: String? = null

    constructor(token: String? = null, otp: String? = null) {
        this.token = token
        this.otp = otp
    }

    @JvmOverloads constructor(isScrapping: Boolean = false) {
        this.isScrapping = isScrapping
    }

    @Throws(IOException::class) override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        val isEnterprise = LinkParserHelper.isEnterprise(original.url.host)
        val authToken = if (token.isNullOrBlank()) if (isEnterprise) PrefGetter.enterpriseToken else PrefGetter.token else token
        val otpCode = if (otp.isNullOrBlank()) if (isEnterprise) PrefGetter.enterpriseOtpCode else PrefGetter.otpCode else otp

        var hasAuth = false
        if (!authToken.isNullOrBlank()) {
            builder.header("Authorization", if (authToken.startsWith("Basic")) authToken else "token $authToken")
            hasAuth = true
        }
        if (!otpCode.isNullOrBlank()) {
            builder.addHeader("X-GitHub-OTP", otpCode.trim())
            hasAuth = true
        }
        if (!hasAuth && original.url.host == "api.github.com" && original.url.pathSegments.last() == "user") {
            throw AuthNullOrBlankException()
        }
        if (!isScrapping) builder.addHeader("User-Agent", "OctoHub")
        val request = builder.build()
        return chain.proceed(request)
    }
}