package com.videochat.data.api

import com.videochat.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Auth
    @POST("api/user/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("api/user/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @GET("api/user/{id}")
    suspend fun getUser(@Path("id") id: Long): Response<User>
    
    @GET("api/user/me")
    suspend fun getMe(): Response<User>
    
    @PUT("api/user")
    suspend fun updateUser(@Body request: UpdateUserRequest): Response<User>
    
    // File upload
    @Multipart
    @POST("api/file/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): Response<FileUploadResponse>
    
    // Friend
    @GET("api/friend/search")
    suspend fun searchUser(@Query("username") username: String): Response<User?>
    
    @POST("api/friend/request")
    suspend fun sendFriendRequest(@Body request: SendFriendRequest): Response<Unit>
    
    @GET("api/friend/request")
    suspend fun getFriendRequests(): Response<List<FriendRequest>>
    
    @PUT("api/friend/request/{id}")
    suspend fun handleFriendRequest(
        @Path("id") id: Long,
        @Body response: FriendRequestResponse
    ): Response<Unit>
    
    @GET("api/friend/list")
    suspend fun getFriendList(): Response<List<User>>
    
    @DELETE("api/friend/{friendId}")
    suspend fun deleteFriend(@Path("friendId") friendId: Long): Response<Unit>
    
    // Message
    @POST("api/message/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<Message>
    
    @GET("api/message/list/{friendId}")
    suspend fun getMessageList(
        @Path("friendId") friendId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): Response<List<Message>>
    
    @PUT("api/message/read/{friendId}")
    suspend fun markAsRead(@Path("friendId") friendId: Long): Response<Unit>
    
    // Call
    @GET("api/call/history")
    suspend fun getCallHistory(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<List<CallRecord>>
}
