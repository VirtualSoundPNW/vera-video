package org.veraproject.veravideo.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CatalogApi {

    /**
     * Fetch the catalog.
     *
     * @param since ISO 8601 cursor from the previous sync; omit for a full fetch.
     * @param ifNoneMatch previous ETag — the backend answers 304 when nothing changed.
     *
     * Returns a raw [Response] rather than the body so the 304 is visible:
     * Retrofit would otherwise surface it as a success with a null body.
     */
    @GET("catalog")
    suspend fun getCatalog(
        @Query("since") since: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<CatalogResponseDto>
}
