package com.owncloud.android.authentication.oauth

import android.net.Uri
import androidx.annotation.WorkerThread
import com.okta.oidc.net.ConnectionParameters
import com.okta.oidc.net.OktaHttpClient
import com.owncloud.android.lib.common.http.HttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

/**
 * A OktaHttpClient implementation using OkHttpClient.
 */
class MyOktaOkhttp : OktaHttpClient {
    /**
     * The Call.
     */
    @Volatile
    protected var mCall: Call? = null

    /**
     * The response.
     */
    protected var mResponse: Response? = null

    /**
     * The exception.
     */
    protected var mException: Exception? = null

    /**
     * Build request request.
     *
     * @param uri   the uri
     * @param param the param
     * @return the request
     */
    protected fun buildRequest(uri: Uri, param: ConnectionParameters): Request {
        if (sOkHttpClient == null) {
            sOkHttpClient = HttpClient.getOkHttpClient()
        }
        var requestBuilder = Request.Builder().url(uri.toString())
        for ((key, value) in param.requestProperties()) {
            requestBuilder.addHeader(key, value!!)
        }
        if (param.requestMethod() == ConnectionParameters.RequestMethod.GET) {
            requestBuilder = requestBuilder.get()
        } else {
            val postParameters = param.postParameters()
            if (postParameters != null) {
                val formBuilder = FormBody.Builder()
                for ((key, value) in postParameters) {
                    formBuilder.add(key, value!!)
                }
                val formBody: RequestBody = formBuilder.build()
                requestBuilder.post(formBody)
            } else {
                requestBuilder.post(RequestBody.create(null, ""))
            }
        }
        return requestBuilder.build()
    }

    @WorkerThread
    @Throws(Exception::class)
    override fun connect(uri: Uri, param: ConnectionParameters): InputStream? {
        val request = buildRequest(uri, param)
        mCall = sOkHttpClient!!.newCall(request)
        val latch = CountDownLatch(1)
        mCall!!.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mException = e
                latch.countDown()
                throw e
            }

            override fun onResponse(call: Call, response: Response) {
                mResponse = response
                latch.countDown()
            }
        })
        latch.await()
        return if (mResponse != null && mResponse!!.body != null) {
            mResponse!!.body!!.byteStream()
        } else null
    }

    override fun cleanUp() {
        //NO-OP
    }

    override fun cancel() {
        if (mCall != null) {
            mCall!!.cancel()
        }
    }

    override fun getHeaderFields(): Map<String, List<String>> {
        return if (mResponse != null) {
            mResponse!!.headers.toMultimap()
        } else mapOf()
    }

    override fun getHeader(header: String): String {
        return if (mResponse != null) {
            mResponse!!.header(header)!!
        } else ""
    }

    @Throws(IOException::class)
    override fun getResponseCode(): Int {
        return if (mResponse != null) {
            mResponse!!.code
        } else -1
    }

    override fun getContentLength(): Int {
        return if (mResponse != null && mResponse!!.body != null) {
            mResponse!!.body!!.contentLength().toInt()
        } else -1
    }

    @Throws(IOException::class)
    override fun getResponseMessage(): String {
        return if (mResponse != null) {
            mResponse!!.message
        } else ""
    }

    companion object {
        private const val TAG = "OkHttp"

        /**
         * The constant sOkHttpClient.
         */
        protected var sOkHttpClient: OkHttpClient? = null
    }
}
