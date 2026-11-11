package com.rayatickets.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class RayaApi {
    companion object {
        const val BASE_URL = "https://raya-atmtickets.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val cookieStore = mutableMapOf<String, Cookie>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(cookieStore) {
                    cookies.forEach { cookieStore[it.name] = it }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return synchronized(cookieStore) {
                    cookieStore.values.toList()
                }
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/login")

            getCsrfToken()?.let { token ->
                requestBuilder.header("X-XSRF-TOKEN", token)
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    var currentUserEmail: String? = null
    var currentUserName: String? = null

    private fun getCsrfToken(): String? {
        return synchronized(cookieStore) {
            val csrfCookie = cookieStore["XSRF-TOKEN"]
            csrfCookie?.let {
                try {
                    URLDecoder.decode(it.value, "UTF-8")
                } catch (e: Exception) {
                    it.value
                }
            }
        }
    }

    suspend fun login(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val initRequest = Request.Builder()
                .url("$BASE_URL/login")
                .get()
                .build()
            client.newCall(initRequest).execute().use { response ->
                if (!response.isSuccessful && response.code != 302) {
                    return@withContext Result.failure(IOException("Failed to connect to login page: ${response.code}"))
                }
            }

            val csrfToken = getCsrfToken()
                ?: return@withContext Result.failure(IOException("CSRF cookie not received from server."))

            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", pass)
                put("remember_me", false)
            }.toString()

            val loginRequest = Request.Builder()
                .url("$BASE_URL/api/auth/login")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Content-Type", "application/json;charset=UTF-8")
                .build()

            client.newCall(loginRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Login failed (${response.code}): $body"))
                }

                val json = JSONObject(body)
                val userObj = json.optJSONObject("user")
                currentUserEmail = userObj?.optString("email", email) ?: email
                val rawName = userObj?.optString("name", "Engineer") ?: "Engineer"
                currentUserName = TicketParser.cleanAssigneeName(rawName)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTickets(
        startDate: LocalDate,
        endDate: LocalDate,
        isPendingOnly: Boolean = false,
        isResolvedOnly: Boolean = false,
        leaderId: Int? = 131
    ): Result<List<Ticket>> = withContext(Dispatchers.IO) {
        try {
            val allTickets = mutableListOf<Ticket>()
            var page = 1
            val perPage = 100

            val startIso = startDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".000Z"
            val endIso = endDate.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".000Z"

            val filtersObj = JSONObject().apply {
                put("start_time", JSONArray().apply {
                    put(startIso)
                    put(endIso)
                })
                if (leaderId != null) {
                    put("leader_id", leaderId)
                }
                if (isPendingOnly && !isResolvedOnly) {
                    put("is_closed", 0)
                    put("status_id", 1)
                } else if (isResolvedOnly && !isPendingOnly) {
                    put("is_closed", 1)
                }
            }

            while (true) {
                val urlBuilder = "$BASE_URL/api/tickets".toHttpUrl().newBuilder()

                urlBuilder.addQueryParameter("q", "")
                urlBuilder.addQueryParameter("perPage", perPage.toString())
                urlBuilder.addQueryParameter("page", page.toString())
                urlBuilder.addQueryParameter("sortBy", "start_time")
                urlBuilder.addQueryParameter("sortDesc", "false")
                urlBuilder.addQueryParameter("filters", filtersObj.toString())

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.code == 401 || response.code == 419) {
                    response.close()
                    return@withContext Result.failure(IOException("Session expired. Please log in again."))
                }

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext Result.failure(IOException("Server error (${response.code}): $body"))
                }
                response.close()

                val tickets = TicketParser.parseJson(body)
                if (tickets.isEmpty()) {
                    break
                }

                allTickets.addAll(tickets)
                if (tickets.size < perPage) {
                    break
                }
                page++
            }

            Result.success(allTickets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}