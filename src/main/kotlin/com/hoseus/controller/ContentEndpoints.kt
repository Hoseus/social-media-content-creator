package com.hoseus.controller

import com.hoseus.content.ContentService
import com.hoseus.redditapi.*
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@Path("/content")
class ContentEndpoints(
    private val contentService: ContentService
) {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun create(createContentDto: CreateContentDto): Uni<RestResponse<SuccessResponse>> =
        this.contentService
            .createFromText(text = createContentDto.text, speakerSex = createContentDto.speakerSex)
            .map { RestResponse.ok(SuccessResponse(status = Status.SUCCESS)) }

    @Path("/reddit-drama")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createRedditDrama(createRedditDramaContentDto: CreateRedditDramaContentDto): Uni<RestResponse<SuccessResponse>> =
        this.contentService
            .createFromRedditDrama(postUrl = createRedditDramaContentDto.postUrl, speakerSex = createRedditDramaContentDto.speakerSex)
            .map { RestResponse.ok(SuccessResponse(status = Status.SUCCESS)) }

    @ServerExceptionMapper
    fun mapException(e: Exception): Uni<RestResponse<ErrorResponse>> {
        return Uni.createFrom().item(
            RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR, ErrorResponse(Status.FAILURE, e.message ?: "Something went wrong"))
        )
    }
}