(ns photosync.model
  (:require
    [hyperion.api :as h]
    [clojure.tools.logging :as log]
    [photosync.util :as util]
    [hyperion.types :as ht]
    [hyperion.gae.types :as gt])
 (:import [com.google.appengine.api.datastore Blob]))


; Login provider is Google - google-user is the owner of all records
; oauth-token can refer to SmugMug and Google
; One SmugMug account per google-user




(h/defentity user-session
     [googleuser-key :type (ht/foreign-key :googleuser)]
     [misc :packer pr-str :unpacker read-string :default "nil"]
     [last-uri]
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



(h/defentity oauth-token
            [__indexed :default [:owner :source]]
            [owner :type (ht/foreign-key :googleuser)]
            [created-at]
            [access_token]
            [refresh_token]
            [source]
            [expires])



(defn save-or-update
  [kind filters update]
  (if-let [record (first (h/find-by-kind kind :filters filters))]
    (:key (h/save (util/safe-merge record update)))
    (:key (h/save {:kind kind} update))))

;access_token  The token that your application sends to authorize a Google API request.
;refresh_token  A token that you can use to obtain a new access token. Refresh tokens are valid until the user revokes access. Note that refresh tokens are always returned for installed applications.
;expires_in  The remaining lifetime of the access token in seconds.
;token_type  The type of token returned. At this time, this field's value is always set to Bearer.


; SmugMug User
; user-uri https://api.smugmug.com/api/v2!authuser
; To browse a user's node hierarchy, you will typically start at the root by following the
; Node link from the User endpoint.


; Populated from 	/api/v2/user/<NICKNAME>
(h/defentity smug-user
 [owner :type (ht/foreign-key :oauth-token)]
 [AccountStatus]
 [FirstName]
 [ImageCount]
 [IsTrial]
 [LastName]
 [NickName]
 [Domain]
 [DomainOnly]
 [Name]
 [node]
 [Plan]
 [bio-thumb]
 [RefTag]
 [WebUri])


(h/defentity smug-node
 [owner :type (ht/foreign-key :smug-user)]
 [updated-at]
 [remaining-nodes]
 [data :type Blob])


(h/defentity SmugAlbum
 [owner :type (ht/foreign-key :oauth-token)])

(h/defentity Image
 [owner :type (ht/foreign-key :oauth-token)])