(ns photosync.model
  (:require
    [hyperion.api :as h]
    [hyperion.types :as ht]))



(h/defentity user-session
     [google-id]
     [session-id]
     [expires]
     [ip]
     [googleuser-key :type (ht/foreign-key :googleuser)]
     [created-at]) ; populated automaticaly

(h/defentity google-user
            [__indexed :default [:email :id]]
            [given_name]
            [email]
            [locale]
            [name]
            [family_name]
            [link]
            [id]
            [picture]
            [verified_email]
            [gender])



(h/defentity OAuthToken
            [__indexed :default [:owner]]
            [owner :type (ht/foreign-key :googleuser)]
            [access_token]
            [refresh_token]
            [source]
            [expires])

;access_token  The token that your application sends to authorize a Google API request.
;refresh_token  A token that you can use to obtain a new access token. Refresh tokens are valid until the user revokes access. Note that refresh tokens are always returned for installed applications.
;expires_in  The remaining lifetime of the access token in seconds.
;token_type  The type of token returned. At this time, this field's value is always set to Bearer.


; SmugMug User
; user-uri https://api.smugmug.com/api/v2!authuser
; To browse a user's node hierarchy, you will typically start at the root by following the
; Node link from the User endpoint.


; Populated from 	/api/v2/user/<NICKNAME>
(h/defentity SmugUser
 [owner :type (ht/foreign-key :googleuser)]
 [AccountStatus]
 [FirstName]
 [ImageCount]
 [IsTrial]
 [LastName]
 [NickName]
 [Domain]
 [DomainOnly]
 [Name]
 [Uri]
 [WebUri])

(h/defentity SmugNode
 [owner :type (ht/foreign-key :googleuser)])



(h/defentity SmugAlbum
 [owner :type (ht/foreign-key :googleuser)])

(h/defentity Image
 [owner :type (ht/foreign-key :googleuser)])