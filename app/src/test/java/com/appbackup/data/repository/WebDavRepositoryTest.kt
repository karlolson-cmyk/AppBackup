package com.appbackup.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebDavRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WebDavRepository

    @Before
    fun setup() {
        server = MockWebServer()
        repository = WebDavRepository()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `test connection succeeds with 207 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(""))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "pass"
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test connection fails with 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "wrong"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `test connection fails with 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "pass"
        )
        assertTrue(result.isFailure)
    }
}
