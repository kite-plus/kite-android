package com.kiteplus.kite.mobile.api.endpoint

import com.kiteplus.kite.mobile.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import okhttp3.ResponseBody
import com.google.gson.annotations.SerializedName

import com.kiteplus.kite.mobile.api.model.AccountInfo
import com.kiteplus.kite.mobile.api.model.AppDetail
import com.kiteplus.kite.mobile.api.model.AppInfo
import com.kiteplus.kite.mobile.api.model.AppInstall
import com.kiteplus.kite.mobile.api.model.AppList
import com.kiteplus.kite.mobile.api.model.AppUpdate
import com.kiteplus.kite.mobile.api.model.ConflictBody
import com.kiteplus.kite.mobile.api.model.ContentTypeList
import com.kiteplus.kite.mobile.api.model.Credentials
import com.kiteplus.kite.mobile.api.model.CredentialsChange
import com.kiteplus.kite.mobile.api.model.DeliveryState
import com.kiteplus.kite.mobile.api.model.Draft
import com.kiteplus.kite.mobile.api.model.ErrorBody
import com.kiteplus.kite.mobile.api.model.GitHubRequest
import com.kiteplus.kite.mobile.api.model.GitHubState
import com.kiteplus.kite.mobile.api.model.Item
import com.kiteplus.kite.mobile.api.model.KiteRelease
import com.kiteplus.kite.mobile.api.model.Media
import com.kiteplus.kite.mobile.api.model.MediaList
import com.kiteplus.kite.mobile.api.model.PasswordConfirmation
import com.kiteplus.kite.mobile.api.model.Plan
import com.kiteplus.kite.mobile.api.model.PluginDetail
import com.kiteplus.kite.mobile.api.model.PluginExists
import com.kiteplus.kite.mobile.api.model.PluginInfo
import com.kiteplus.kite.mobile.api.model.PluginInfoList
import com.kiteplus.kite.mobile.api.model.PluginSwitch
import com.kiteplus.kite.mobile.api.model.Preview
import com.kiteplus.kite.mobile.api.model.PreviewBody
import com.kiteplus.kite.mobile.api.model.Profile
import com.kiteplus.kite.mobile.api.model.PublishBody
import com.kiteplus.kite.mobile.api.model.PublishRefused
import com.kiteplus.kite.mobile.api.model.PushBody
import com.kiteplus.kite.mobile.api.model.Result
import com.kiteplus.kite.mobile.api.model.SessionInfo
import com.kiteplus.kite.mobile.api.model.Settings
import com.kiteplus.kite.mobile.api.model.SetupRequest
import com.kiteplus.kite.mobile.api.model.SetupState
import com.kiteplus.kite.mobile.api.model.SiteInfo
import com.kiteplus.kite.mobile.api.model.SummaryList
import com.kiteplus.kite.mobile.api.model.TaxonomyList
import com.kiteplus.kite.mobile.api.model.TermCountList
import com.kiteplus.kite.mobile.api.model.TermDetail
import com.kiteplus.kite.mobile.api.model.TermRename
import com.kiteplus.kite.mobile.api.model.ThemeDetail
import com.kiteplus.kite.mobile.api.model.ThemeExists
import com.kiteplus.kite.mobile.api.model.ThemeInfo
import com.kiteplus.kite.mobile.api.model.ThemeInfoList
import com.kiteplus.kite.mobile.api.model.UpdateNeeds
import com.kiteplus.kite.mobile.api.model.UpdatePlan
import com.kiteplus.kite.mobile.api.model.WordCount

import okhttp3.MultipartBody

interface DefaultApi {
    /**
     * Forget a preview.
     * 
     * Responses:
     *  - 204: Forgotten.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *
     * @param token 
     * @return [Unit]
     */
    @DELETE("previews/{token}")
    suspend fun closePreview(@Path("token") token: kotlin.String): Response<Unit>

    /**
     * Link the project to a repository on GitHub with a fine-grained token for it, and push it there: start the repository and add origin where needed, write the deploy workflow and turn Pages on when asked, commit the site&#39;s own files and push. What would have to be refused is refused before anything changes.
     * 
     * Responses:
     *  - 200: What the connect did.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: The publish did not fully happen.
     *  - 501: Failed.
     *
     * @param gitHubRequest 
     * @return [GitHubState]
     */
    @PUT("github")
    suspend fun connectGitHub(@Body gitHubRequest: GitHubRequest): Response<GitHubState>

    /**
     * Count a draft&#39;s words as its page will: paragraphs, list items, headings and table cells, without code blocks or image alt text.
     * 
     * Responses:
     *  - 200: The count.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 501: Failed.
     *
     * @param draft 
     * @param id The item being edited, so it is counted where it lives. (optional)
     * @return [WordCount]
     */
    @POST("wordcount")
    suspend fun countWords(@Body draft: Draft, @Query("id") id: kotlin.String? = null): Response<WordCount>

    /**
     * Write a new item.
     * 
     * Responses:
     *  - 201: The item as stored.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *
     * @param draft 
     * @return [Item]
     */
    @POST("contents")
    suspend fun createContent(@Body draft: Draft): Response<Item>

    /**
     * Move an item to the trash, keeping its files so it can be restored.
     * 
     * Responses:
     *  - 204: Moved to the trash.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: The item changed since it was loaded.
     *  - 428: Failed.
     *
     * @param id 
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @return [Unit]
     */
    @DELETE("contents/{id}")
    suspend fun deleteContent(@Path("id") id: kotlin.String, @Header("If-Match") ifMatch: kotlin.String): Response<Unit>

    /**
     * Remove a file from a page&#39;s bundle, named by its path within it.
     * 
     * Responses:
     *  - 204: Removed.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *
     * @param id 
     * @param name 
     * @return [Unit]
     */
    @DELETE("contents/{id}/media/{name}")
    suspend fun deleteMedia(@Path("id") id: kotlin.String, @Path("name") name: kotlin.String): Response<Unit>

    /**
     * Forget the token the studio saved. One the environment gives stays.
     * 
     * Responses:
     *  - 200: The link as it now is.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 501: Failed.
     *
     * @return [GitHubState]
     */
    @DELETE("github")
    suspend fun disconnectGitHub(): Response<GitHubState>

    /**
     * Sign out every browser but the caller&#39;s, whose session is issued again in Set-Cookie.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 501: Failed.
     *
     * @return [AccountInfo]
     */
    @DELETE("account/sessions")
    suspend fun endOtherSessions(): Response<AccountInfo>

    /**
     * Build the site as a deployment gets it, without drafts, and answer with it as a zip archive of what kite build writes, to upload anywhere that serves static files.
     * 
     * Responses:
     *  - 200: The site. Content-Disposition names the archive.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 409: The site could not be built as it stands.
     *  - 501: Failed.
     *
     * @return [ResponseBody]
     */
    @POST("export")
    suspend fun exportSite(): Response<ResponseBody>

    /**
     * Describe the person using the studio and how they sign in.
     * 
     * Responses:
     *  - 200: The account and profile.
     *  - 401: Not signed in.
     *
     * @return [AccountInfo]
     */
    @GET("account")
    suspend fun getAccount(): Response<AccountInfo>

    /**
     * Describe a theme or a plugin in the index with every version it lists, newest first.
     * 
     * Responses:
     *  - 200: The package.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 501: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @return [AppDetail]
     */
    @GET("apps/{kind}/{id}")
    suspend fun getApp(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String): Response<AppDetail>

    /**
     * Read a package&#39;s icon, which this server fetches as it fetches a screenshot; an SVG is served sandboxed.
     * 
     * Responses:
     *  - 200: The icon.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @return [ResponseBody]
     */
    @GET("apps/{kind}/{id}/icon")
    suspend fun getAppIcon(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String): Response<ResponseBody>

    /**
     * Read a theme&#39;s picture of itself, which this server fetches, so that a browser never has to reach the index&#39;s addresses.
     * 
     * Responses:
     *  - 200: The picture.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @return [ResponseBody]
     */
    @GET("apps/{kind}/{id}/screenshot")
    suspend fun getAppScreenshot(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String): Response<ResponseBody>

    /**
     * Read the picture shown beside the name.
     * 
     * Responses:
     *  - 200: The picture.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @return [ResponseBody]
     */
    @GET("account/avatar")
    suspend fun getAvatar(): Response<ResponseBody>

    /**
     * Read one item, including its source body.
     * 
     * Responses:
     *  - 200: The item. ETag carries its revision.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @param id 
     * @return [Item]
     */
    @GET("contents/{id}")
    suspend fun getContent(@Path("id") id: kotlin.String): Response<Item>

    /**
     * Report how far the content has traveled.
     * 
     * Responses:
     *  - 200: The delivery state.
     *  - 401: Not signed in.
     *
     * @return [DeliveryState]
     */
    @GET("publish")
    suspend fun getDeliveryState(): Response<DeliveryState>

    /**
     * Report how the project is linked to GitHub: where the token comes from, never the token itself, and the repository the remote points to.
     * 
     * Responses:
     *  - 200: The link.
     *  - 401: Not signed in.
     *  - 501: Failed.
     *
     * @return [GitHubState]
     */
    @GET("github")
    suspend fun getGitHub(): Response<GitHubState>

    /**
     * Describe a plugin with its settings form and what each setting holds. ETag carries the revision of kite.yaml, for saving plugins.settings.&lt;id&gt;.
     * 
     * Responses:
     *  - 200: The plugin.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 501: Failed.
     *
     * @param id 
     * @return [PluginDetail]
     */
    @GET("plugins/{id}")
    suspend fun getPlugin(@Path("id") id: kotlin.String): Response<PluginDetail>

    /**
     * Read a page or a file of a preview. Its links lead to its other pages, drawn the same way.
     * 
     * Responses:
     *  - 200: The page or file.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 501: Failed.
     *
     * @param token 
     * @param path Where the page or file is in the site; it may hold slashes.
     * @return [kotlin.String]
     */
    @GET("previews/{token}/{path}")
    suspend fun getPreviewPage(@Path("token") token: kotlin.String, @Path("path") path: kotlin.String): Response<kotlin.String>

    /**
     * Report whether this server needs a sign-in, and whether the caller has one.
     * 
     * Responses:
     *  - 200: Whether a session is needed, and the current one.
     *
     * @return [SessionInfo]
     */
    @GET("auth/session")
    suspend fun getSession(): Response<SessionInfo>

    /**
     * Read what can be configured, and what it is set to.
     * 
     * Responses:
     *  - 200: The settings. ETag carries the revision of kite.yaml.
     *  - 401: Not signed in.
     *
     * @return [Settings]
     */
    @GET("settings")
    suspend fun getSettings(): Response<Settings>

    /**
     * Report whether this server still has to be set up.
     * 
     * Responses:
     *  - 200: Whether setup is needed, and what to fill the form with.
     *
     * @return [SetupState]
     */
    @GET("setup")
    suspend fun getSetupState(): Response<SetupState>

    /**
     * Describe the open project.
     * 
     * Responses:
     *  - 200: The project.
     *  - 401: Not signed in.
     *
     * @return [SiteInfo]
     */
    @GET("site")
    suspend fun getSite(): Response<SiteInfo>

    /**
     * Read one term with every item that carries it, however each writes it, trashed ones included.
     * 
     * Responses:
     *  - 200: The term and its items. ETag fingerprints them for a rename or removal.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @param taxonomy 
     * @param term 
     * @return [TermDetail]
     */
    @GET("taxonomies/{taxonomy}/terms/{term}")
    suspend fun getTerm(@Path("taxonomy") taxonomy: kotlin.String, @Path("term") term: kotlin.String): Response<TermDetail>

    /**
     * Describe a theme with what it can be configured with and what it would read from kite.yaml. ETag carries the revision of kite.yaml, for saving.
     * 
     * Responses:
     *  - 200: The theme.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 501: Failed.
     *
     * @param name 
     * @return [ThemeDetail]
     */
    @GET("themes/{name}")
    suspend fun getTheme(@Path("name") name: kotlin.String): Response<ThemeDetail>

    /**
     * Read a theme&#39;s picture of itself.
     * 
     * Responses:
     *  - 200: The picture.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @param name 
     * @return [ResponseBody]
     */
    @GET("themes/{name}/screenshot")
    suspend fun getThemeScreenshot(@Path("name") name: kotlin.String): Response<ResponseBody>

    /**
     * Install a theme or a plugin from the index: the version asked for, or the newest that works with this Kite. This server fetches the archive, checks it against the sha256 the index gives and then as an uploaded one is checked, and kite.lock records where it came from. A plugin is installed turned off.
     * 
     * Responses:
     *  - 201: The package, as the site now has it.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 501: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @param replace Replace a theme or a plugin of the same name the site has. Without it, one the site has is answered with 409. (optional)
     * @param appInstall  (optional)
     * @return [AppInfo]
     */
    @POST("apps/{kind}/{id}/install")
    suspend fun installApp(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String, @Query("replace") replace: kotlin.Boolean? = null, @Body appInstall: AppInstall? = null): Response<AppInfo>

    /**
     * Install a plugin from a zip archive holding plugin.yaml at its top or in one folder. It is checked the way a site checks it when it loads, and is not turned on: plugins.enabled in the settings does that.
     * 
     * Responses:
     *  - 201: The installed plugin.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: A plugin of that id is installed already.
     *  - 413: Failed.
     *  - 501: Failed.
     *
     * @param file 
     * @param replace Replace an installed plugin of the same id. Without it, one already installed is answered with 409 and both versions. (optional)
     * @return [PluginInfo]
     */
    @Multipart
    @POST("plugins")
    suspend fun installPlugin(@Part file: MultipartBody.Part, @Query("replace") replace: kotlin.Boolean? = null): Response<PluginInfo>

    /**
     * Install a theme from a zip archive holding theme.yaml at its top or in one folder. It is checked the way a theme is checked when a site loads it.
     * 
     * Responses:
     *  - 201: The installed theme.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: A theme of that name is installed already.
     *  - 413: Failed.
     *  - 501: Failed.
     *
     * @param file 
     * @param replace Replace an installed theme of the same name. Without it, one already installed is answered with 409 and both versions. (optional)
     * @return [ThemeInfo]
     */
    @Multipart
    @POST("themes")
    suspend fun installTheme(@Part file: MultipartBody.Part, @Query("replace") replace: kotlin.Boolean? = null): Response<ThemeInfo>

    /**
     * List the themes and plugins in the index the site installs from, with how the site stands with each: installed, a newer version that works with this Kite, a yanked version. A package no longer listed is left out unless the site has it. The index is fetched by this server, at most once an hour, and the copy kept is used, and said to be, when it cannot be fetched.
     * 
     * Responses:
     *  - 200: What the index lists.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 501: Failed.
     *  - 502: Failed.
     *
     * @param kind theme or plugin; both when absent. (optional)
     * @param q Words every package listed has to match, in its id, title, description or tags. (optional)
     * @param refresh Fetch the index even if the copy kept is less than an hour old. (optional)
     * @return [AppList]
     */
    @GET("apps")
    suspend fun listApps(@Query("kind") kind: kotlin.String? = null, @Query("q") q: kotlin.String? = null, @Query("refresh") refresh: kotlin.Boolean? = null): Response<AppList>

    /**
     * List content types and the field schema forms are generated from.
     * 
     * Responses:
     *  - 200: The registry.
     *  - 401: Not signed in.
     *
     * @return [ContentTypeList]
     */
    @GET("content-types")
    suspend fun listContentTypes(): Response<ContentTypeList>

    /**
     * List content.
     * 
     * Responses:
     *  - 200: One page of items.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *
     * @param kind Content kind, repeatable. (optional)
     * @param status draft, scheduled, published or archived, repeatable. (optional)
     * @param locale Locale, repeatable. (optional)
     * @param id Restrict to these ids, repeatable. (optional)
     * @param term taxonomy:term the item must carry at least one of, repeatable. (optional)
     * @param termAll taxonomy:term the item must carry all of, repeatable. (optional)
     * @param publishedFrom Earliest publication time, RFC 3339. (optional)
     * @param publishedTo Latest publication time, RFC 3339. (optional)
     * @param updatedFrom Earliest update time, RFC 3339. (optional)
     * @param updatedTo Latest update time, RFC 3339. (optional)
     * @param q Free text over title, excerpt and body. (optional)
     * @param includeDeleted Include soft deleted items. (optional)
     * @param deletedOnly Return only soft deleted items. (optional)
     * @param sort Comma separated keys, &#39;-&#39; for descending, e.g. -published_at,title. (optional)
     * @param cursor Opaque position from a previous response. There is no offset. (optional)
     * @param limit Items per page, capped by the server. (optional)
     * @param count Also report the size of the whole filtered set. (optional)
     * @return [SummaryList]
     */
    @GET("contents")
    suspend fun listContents(@Query("kind") kind: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("status") status: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("locale") locale: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("id") id: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("term") term: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("term_all") termAll: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("published_from") publishedFrom: kotlin.String? = null, @Query("published_to") publishedTo: kotlin.String? = null, @Query("updated_from") updatedFrom: kotlin.String? = null, @Query("updated_to") updatedTo: kotlin.String? = null, @Query("q") q: kotlin.String? = null, @Query("include_deleted") includeDeleted: kotlin.Boolean? = null, @Query("deleted_only") deletedOnly: kotlin.String? = null, @Query("sort") sort: kotlin.String? = null, @Query("cursor") cursor: kotlin.String? = null, @Query("limit") limit: kotlin.Int? = null, @Query("count") count: kotlin.Boolean? = null): Response<SummaryList>

    /**
     * List the files an item keeps beside it, which are published with its page.
     * 
     * Responses:
     *  - 200: The item's files, by their path within its bundle.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @param id 
     * @return [MediaList]
     */
    @GET("contents/{id}/media")
    suspend fun listMedia(@Path("id") id: kotlin.String): Response<MediaList>

    /**
     * List the plugins in plugins/, including the ones that cannot be used, with why, in the language Accept-Language asks for when a plugin has a pack for it.
     * 
     * Responses:
     *  - 200: The plugins.
     *  - 401: Not signed in.
     *  - 501: Failed.
     *
     * @return [PluginInfoList]
     */
    @GET("plugins")
    suspend fun listPlugins(): Response<PluginInfoList>

    /**
     * List taxonomies and how many terms each holds.
     * 
     * Responses:
     *  - 200: The taxonomies.
     *  - 401: Not signed in.
     *
     * @return [TaxonomyList]
     */
    @GET("taxonomies")
    suspend fun listTaxonomies(): Response<TaxonomyList>

    /**
     * Count one taxonomy&#39;s terms over a filtered set.
     * 
     * Responses:
     *  - 200: Term counts, most used first.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *
     * @param taxonomy 
     * @param kind Content kind, repeatable. (optional)
     * @param status draft, scheduled, published or archived, repeatable. (optional)
     * @param locale Locale, repeatable. (optional)
     * @param id Restrict to these ids, repeatable. (optional)
     * @param term taxonomy:term the item must carry at least one of, repeatable. (optional)
     * @param termAll taxonomy:term the item must carry all of, repeatable. (optional)
     * @param publishedFrom Earliest publication time, RFC 3339. (optional)
     * @param publishedTo Latest publication time, RFC 3339. (optional)
     * @param updatedFrom Earliest update time, RFC 3339. (optional)
     * @param updatedTo Latest update time, RFC 3339. (optional)
     * @param q Free text over title, excerpt and body. (optional)
     * @param includeDeleted Include soft deleted items. (optional)
     * @param deletedOnly Return only soft deleted items. (optional)
     * @param sort Comma separated keys, &#39;-&#39; for descending, e.g. -published_at,title. (optional)
     * @param cursor Opaque position from a previous response. There is no offset. (optional)
     * @param limit Items per page, capped by the server. (optional)
     * @param count Also report the size of the whole filtered set. (optional)
     * @return [TermCountList]
     */
    @GET("taxonomies/{taxonomy}/terms")
    suspend fun listTerms(@Path("taxonomy") taxonomy: kotlin.String, @Query("kind") kind: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("status") status: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("locale") locale: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("id") id: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("term") term: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("term_all") termAll: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("published_from") publishedFrom: kotlin.String? = null, @Query("published_to") publishedTo: kotlin.String? = null, @Query("updated_from") updatedFrom: kotlin.String? = null, @Query("updated_to") updatedTo: kotlin.String? = null, @Query("q") q: kotlin.String? = null, @Query("include_deleted") includeDeleted: kotlin.Boolean? = null, @Query("deleted_only") deletedOnly: kotlin.String? = null, @Query("sort") sort: kotlin.String? = null, @Query("cursor") cursor: kotlin.String? = null, @Query("limit") limit: kotlin.Int? = null, @Query("count") count: kotlin.Boolean? = null): Response<TermCountList>

    /**
     * List the themes the project could use: the one built into Kite, then each directory of themes/, including the ones that cannot be used, with why. What a theme says about itself is in the language Accept-Language asks for, when the theme has a language pack for it.
     * 
     * Responses:
     *  - 200: The themes.
     *  - 401: Not signed in.
     *  - 501: Failed.
     *
     * @return [ThemeInfoList]
     */
    @GET("themes")
    suspend fun listThemes(): Response<ThemeInfoList>

    /**
     * Exchange credentials for a session cookie.
     * 
     * Responses:
     *  - 200: The session. The cookie is in Set-Cookie.
     *  - 400: Failed.
     *  - 401: Failed.
     *  - 403: The request came from another site.
     *  - 429: Failed.
     *
     * @param credentials 
     * @return [SessionInfo]
     */
    @POST("auth/login")
    suspend fun login(@Body credentials: Credentials): Response<SessionInfo>

    /**
     * Discard the session cookie.
     * 
     * Responses:
     *  - 204: Signed out.
     *  - 403: The request came from another site.
     *
     * @return [Unit]
     */
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    /**
     * Draw the site with a theme or settings being tried, without writing anything. The preview is kept while it is used.
     * 
     * Responses:
     *  - 201: The open preview.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 501: Failed.
     *
     * @param previewBody 
     * @return [Preview]
     */
    @POST("previews")
    suspend fun openPreview(@Body previewBody: PreviewBody): Response<Preview>

    /**
     * Pin this server&#39;s Kite release in kite.lock, with the sha256 of its checksums when they can be fetched, so that kitew and the deploy build the site with it.
     * 
     * Responses:
     *  - 200: Which release builds the site now.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *
     * @return [KiteRelease]
     */
    @POST("site/pin")
    suspend fun pinKite(): Response<KiteRelease>

    /**
     * Say what updating a package the site has from the index would do: the version it goes to, files changed by hand that it would replace, and what a plugin&#39;s new version does beyond what it did.
     * 
     * Responses:
     *  - 200: What the update would do.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 404: Failed.
     *  - 501: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @param version The version to go to; the newest that works with this Kite when absent. (optional)
     * @return [UpdatePlan]
     */
    @GET("apps/{kind}/{id}/update")
    suspend fun planAppUpdate(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String, @Query("version") version: kotlin.String? = null): Response<UpdatePlan>

    /**
     * Report what a publish would do, changing nothing.
     * 
     * Responses:
     *  - 200: The plan, with everything wrong with it.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 501: Failed.
     *
     * @param publishBody 
     * @return [Plan]
     */
    @POST("publish/preflight")
    suspend fun preflightPublish(@Body publishBody: PublishBody): Response<Plan>

    /**
     * Render a draft through the real theme, without storing it.
     * 
     * Responses:
     *  - 200: The rendered page.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 501: Failed.
     *
     * @param draft 
     * @param id The item being edited, so its own images resolve. (optional)
     * @return [kotlin.String]
     */
    @POST("preview")
    suspend fun preview(@Body draft: Draft, @Query("id") id: kotlin.String? = null): Response<kotlin.String>

    /**
     * Commit the named content, and push it when asked to.
     * 
     * Responses:
     *  - 200: What was published.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: The publish did not fully happen.
     *  - 501: Failed.
     *
     * @param publishBody 
     * @return [Result]
     */
    @POST("publish")
    suspend fun publish(@Body publishBody: PublishBody): Response<Result>

    /**
     * Push what is already committed, replaying it onto a remote that moved on when asked to.
     * 
     * Responses:
     *  - 200: What was pushed.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: The publish did not fully happen.
     *  - 501: Failed.
     *
     * @param pushBody 
     * @return [Result]
     */
    @POST("publish/push")
    suspend fun push(@Body pushBody: PushBody): Response<Result>

    /**
     * Go back to initials.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 501: Failed.
     *
     * @return [AccountInfo]
     */
    @DELETE("account/avatar")
    suspend fun removeAvatar(): Response<AccountInfo>

    /**
     * Take the password away, leaving the studio open. Only a server that listens on this machine alone allows it.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The current password is wrong, or the request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 429: Failed.
     *  - 501: Failed.
     *
     * @param passwordConfirmation 
     * @return [AccountInfo]
     */
    @DELETE("account/credentials")
    suspend fun removeCredentials(@Body passwordConfirmation: PasswordConfirmation): Response<AccountInfo>

    /**
     * Remove an installed plugin. One that is turned on stays.
     * 
     * Responses:
     *  - 204: Removed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *
     * @param id 
     * @return [Unit]
     */
    @DELETE("plugins/{id}")
    suspend fun removePlugin(@Path("id") id: kotlin.String): Response<Unit>

    /**
     * Take a term off every item that carries it. The items stay.
     * 
     * Responses:
     *  - 204: Taken off every item.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 428: Failed.
     *
     * @param taxonomy 
     * @param term 
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @return [Unit]
     */
    @DELETE("taxonomies/{taxonomy}/terms/{term}")
    suspend fun removeTerm(@Path("taxonomy") taxonomy: kotlin.String, @Path("term") term: kotlin.String, @Header("If-Match") ifMatch: kotlin.String): Response<Unit>

    /**
     * Remove an installed theme. The built-in theme and the one in use stay.
     * 
     * Responses:
     *  - 204: Removed.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *
     * @param name 
     * @return [Unit]
     */
    @DELETE("themes/{name}")
    suspend fun removeTheme(@Path("name") name: kotlin.String): Response<Unit>

    /**
     * Rename a term on every item that carries it, however each writes it, merging it into another term when the new name is already in use.
     * 
     * Responses:
     *  - 200: The term under its new name.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 428: Failed.
     *
     * @param taxonomy 
     * @param term 
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @param termRename 
     * @return [TermDetail]
     */
    @PUT("taxonomies/{taxonomy}/terms/{term}")
    suspend fun renameTerm(@Path("taxonomy") taxonomy: kotlin.String, @Path("term") term: kotlin.String, @Header("If-Match") ifMatch: kotlin.String, @Body termRename: TermRename): Response<TermDetail>

    /**
     * Put a new version of a file of a page&#39;s bundle in its place, under its name, named by its path within the bundle.
     * 
     * Responses:
     *  - 200: The file as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 413: Failed.
     *  - 415: Failed.
     *
     * @param id 
     * @param name 
     * @param file 
     * @return [Media]
     */
    @Multipart
    @PUT("contents/{id}/media/{name}")
    suspend fun replaceMedia(@Path("id") id: kotlin.String, @Path("name") name: kotlin.String, @Part file: MultipartBody.Part): Response<Media>

    /**
     * Restore an item from the trash.
     * 
     * Responses:
     *  - 200: The item as restored.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: The item changed since it was loaded.
     *  - 428: Failed.
     *
     * @param id 
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @return [Item]
     */
    @POST("contents/{id}/restore")
    suspend fun restoreContent(@Path("id") id: kotlin.String, @Header("If-Match") ifMatch: kotlin.String): Response<Item>

    /**
     * Give a studio with no password one, or change the name and password of a guarded one. A change ends every other session; the caller&#39;s is issued again in Set-Cookie.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The current password is wrong, or the request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 429: Failed.
     *  - 501: Failed.
     *
     * @param credentialsChange 
     * @return [AccountInfo]
     */
    @PUT("account/credentials")
    suspend fun setCredentials(@Body credentialsChange: CredentialsChange): Response<AccountInfo>

    /**
     * Describe the site and create the account that guards it; in a folder with no site yet, create the site instead, with no account.
     * 
     * Responses:
     *  - 200: Set up, and signed in. The cookie is in Set-Cookie.
     *  - 400: Failed.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *
     * @param setupRequest 
     * @return [SessionInfo]
     */
    @POST("setup")
    suspend fun setUp(@Body setupRequest: SetupRequest): Response<SessionInfo>

    /**
     * Turn a plugin on, to run after the ones already on, or off. It changes plugins.enabled by one; one that cannot load is not turned on.
     * 
     * Responses:
     *  - 200: The plugin as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: Failed.
     *
     * @param id 
     * @param pluginSwitch 
     * @return [PluginInfo]
     */
    @PUT("plugins/{id}/enabled")
    suspend fun switchPlugin(@Path("id") id: kotlin.String, @Body pluginSwitch: PluginSwitch): Response<PluginInfo>

    /**
     * Update a package the site has from the index. One whose files changed since it was installed, or a plugin whose new version does more, is updated only when the request says that was agreed to; settings stay as they are.
     * 
     * Responses:
     *  - 200: The package, as the site now has it.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: The update needs an agreement it was not given.
     *  - 501: Failed.
     *  - 502: Failed.
     *
     * @param kind 
     * @param id 
     * @param appUpdate 
     * @return [AppInfo]
     */
    @POST("apps/{kind}/{id}/update")
    suspend fun updateApp(@Path("kind") kind: kotlin.String, @Path("id") id: kotlin.String, @Body appUpdate: AppUpdate): Response<AppInfo>

    /**
     * Replace an item, refusing an edit made against a replaced version.
     * 
     * Responses:
     *  - 200: The item as stored.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 409: The item changed since it was loaded.
     *  - 428: Failed.
     *
     * @param id 
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @param draft 
     * @return [Item]
     */
    @PUT("contents/{id}")
    suspend fun updateContent(@Path("id") id: kotlin.String, @Header("If-Match") ifMatch: kotlin.String, @Body draft: Draft): Response<Item>

    /**
     * Draw an open preview again with another theme or other settings.
     * 
     * Responses:
     *  - 200: The preview, at the same address.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 501: Failed.
     *
     * @param token 
     * @param previewBody 
     * @return [Preview]
     */
    @PUT("previews/{token}")
    suspend fun updatePreview(@Path("token") token: kotlin.String, @Body previewBody: PreviewBody): Response<Preview>

    /**
     * Change the name and email address the studio shows. They are kept in .kite/secrets, never published.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 501: Failed.
     *
     * @param profile 
     * @return [AccountInfo]
     */
    @PUT("account/profile")
    suspend fun updateProfile(@Body profile: Profile): Response<AccountInfo>

    /**
     * Change configuration values by dotted path.
     * 
     * Responses:
     *  - 200: The settings as stored.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 409: Failed.
     *  - 428: Failed.
     *
     * @param ifMatch The revision this edit was made against, as returned in ETag. Required: without it a save would overwrite whatever is there.
     * @param requestBody 
     * @return [Settings]
     */
    @PUT("settings")
    suspend fun updateSettings(@Header("If-Match") ifMatch: kotlin.String, @Body requestBody: kotlin.collections.Map<kotlin.String, kotlin.Any>): Response<Settings>

    /**
     * Choose the picture: PNG, JPEG, GIF or WebP, up to 1 MB.
     * 
     * Responses:
     *  - 200: The account as it now is.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 413: Failed.
     *  - 415: Failed.
     *  - 501: Failed.
     *
     * @param file 
     * @return [AccountInfo]
     */
    @Multipart
    @PUT("account/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<AccountInfo>

    /**
     * Store a file beside a page and report the link that reaches it.
     * 
     * Responses:
     *  - 201: The stored file. The name may differ from the one sent, since an upload never replaces a file of the same name.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 404: Failed.
     *  - 405: Failed.
     *  - 413: Failed.
     *  - 415: Failed.
     *
     * @param id 
     * @param file 
     * @return [Media]
     */
    @Multipart
    @POST("contents/{id}/media")
    suspend fun uploadMedia(@Path("id") id: kotlin.String, @Part file: MultipartBody.Part): Response<Media>

    /**
     * Store a file that belongs to the site rather than one page, such as a logo a theme setting names, in static/uploads.
     * 
     * Responses:
     *  - 201: The stored file. Link is its path in the site, which is what a setting holds.
     *  - 400: Failed.
     *  - 401: Not signed in.
     *  - 403: The request came from another site.
     *  - 405: Failed.
     *  - 413: Failed.
     *  - 415: Failed.
     *
     * @param file 
     * @return [Media]
     */
    @Multipart
    @POST("media")
    suspend fun uploadSiteMedia(@Part file: MultipartBody.Part): Response<Media>

}
