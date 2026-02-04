package com.blockbuster.plugin.spotify

import com.blockbuster.db.FlywayManager
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.sqlite.SQLiteDataSource
import java.sql.Connection

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteSpotifyTokenStoreTest {
    private lateinit var dataSource: SQLiteDataSource
    private lateinit var connection: Connection
    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private val testDbPath = "file:tokenStoreTestDb?mode=memory&cache=shared"

    @BeforeEach
    fun setUp() {
        dataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:$testDbPath"
            }
        connection = dataSource.connection
        val flywayManager = FlywayManager(dataSource, testDbPath)
        flywayManager.migrate()

        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        connection.close()
    }

    private fun createTokenStore(): SqliteSpotifyTokenStore {
        return SqliteSpotifyTokenStore(
            dataSource = dataSource,
            httpClient = httpClient,
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            tokenUrl = mockServer.url("/api/token").toString(),
        )
    }

    @Test
    fun `isAuthenticated should return false when no tokens stored`() {
        val store = createTokenStore()

        assertFalse(store.isAuthenticated())
    }

    @Test
    fun `isAuthenticated should return true after storing tokens`() {
        val store = createTokenStore()

        store.storeTokens("access-token-123", "refresh-token-456", 3600)

        assertTrue(store.isAuthenticated())
    }

    @Test
    fun `storeTokens should persist refresh token to database`() {
        val store = createTokenStore()

        store.storeTokens("access-xyz", "refresh-abc", 3600)

        val sql = "SELECT token_value FROM auth_tokens WHERE plugin = 'spotify' AND token_type = 'refresh_token'"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                assertTrue(rs.next())
                assertEquals("refresh-abc", rs.getString("token_value"))
            }
        }
    }

    @Test
    fun `getAccessToken should return cached token when not expired`() {
        val store = createTokenStore()

        store.storeTokens("cached-access-token", "refresh-token", 3600)

        val token = store.getAccessToken()
        assertEquals("cached-access-token", token)
    }

    @Test
    fun `getAccessToken should return null when no refresh token exists`() {
        val store = createTokenStore()

        val token = store.getAccessToken()
        assertNull(token)
    }

    @Test
    fun `getAccessToken should refresh when token is expired`() {
        val store = createTokenStore()

        // Store tokens with 0 seconds expiry (immediately expired)
        store.storeTokens("old-access-token", "stored-refresh-token", 0)

        // Mock the Spotify token endpoint for refresh
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "refreshed-access-token",
                        "token_type": "Bearer",
                        "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
        )

        val token = store.getAccessToken()

        assertEquals("refreshed-access-token", token)

        // Verify the refresh request was made
        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=stored-refresh-token"))
        assertTrue(body.contains("client_id=test-client-id"))
        assertTrue(body.contains("client_secret=test-client-secret"))
    }

    @Test
    fun `getAccessToken should return null when refresh fails`() {
        val store = createTokenStore()

        // Store tokens with 0 seconds expiry (immediately expired)
        store.storeTokens("expired-token", "bad-refresh-token", 0)

        // Mock a failed refresh response
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"invalid_grant"}"""),
        )

        val token = store.getAccessToken()
        assertNull(token)
    }

    @Test
    fun `storeTokens should update existing refresh token via upsert`() {
        val store = createTokenStore()

        store.storeTokens("access-1", "refresh-1", 3600)
        store.storeTokens("access-2", "refresh-2", 3600)

        // Verify only one row exists
        val sql = "SELECT COUNT(*) as cnt FROM auth_tokens WHERE plugin = 'spotify' AND token_type = 'refresh_token'"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                rs.next()
                assertEquals(1, rs.getInt("cnt"))
            }
        }

        // Verify updated value is returned
        assertEquals("access-2", store.getAccessToken())
    }

    @Test
    fun `storeTokens should skip refresh token when null`() {
        val store = createTokenStore()

        // First store a real refresh token
        store.storeTokens("access-1", "refresh-1", 3600)

        // Then store with null refresh token (like a refresh response)
        store.storeTokens("access-2", null, 3600)

        // The original refresh token should still be in the DB
        val sql = "SELECT token_value FROM auth_tokens WHERE plugin = 'spotify' AND token_type = 'refresh_token'"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                assertTrue(rs.next())
                assertEquals("refresh-1", rs.getString("token_value"))
            }
        }

        // But the cached access token should be the new one
        assertEquals("access-2", store.getAccessToken())
    }

    @Test
    fun `refresh should store new refresh token from response`() {
        val store = createTokenStore()

        // Store initial tokens (expired)
        store.storeTokens("old-access", "old-refresh", 0)

        // Mock refresh response that includes a new refresh token
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "new-access-token",
                        "refresh_token": "new-refresh-token",
                        "token_type": "Bearer",
                        "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
        )

        val token = store.getAccessToken()
        assertNotNull(token)
        assertEquals("new-access-token", token)

        // Verify the new refresh token was persisted
        val sql = "SELECT token_value FROM auth_tokens WHERE plugin = 'spotify' AND token_type = 'refresh_token'"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                assertTrue(rs.next())
                assertEquals("new-refresh-token", rs.getString("token_value"))
            }
        }
    }
}
