(ns photosync.smugmug
  (:use compojure.core)
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]

            [ring.util.response :refer [redirect response]]
            [compojure.core :refer [defroutes ANY GET POST]]
            [ring.util.response :as response]
            [clojure.java.io :as io]
            [cheshire.core :as parse]
            [hyperion.api :as ds]
            [java-time.temporal :refer [instant]]
            [java-time.core :refer [plus]]
            [java-time.pre-java8 :refer [java-date]]
            [java-time.amount :refer [seconds]]
            [photosync.model :as models]
            [photosync.util :as util]
            [oauth.client :as oauth])
  (:import (com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value)))



;; Create a Consumer, in this case one to access Twitter.
;; Register an application at Twitter (https://dev.twitter.com/apps/new)
;; to obtain a Consumer token and token secret.
(def consumer (oauth/make-consumer "***REMOVED***"
                                   "***REMOVED***"
                                   "https://api.smugmug.com/services/oauth/1.0a/getRequestToken"
                                   "https://api.smugmug.com/services/oauth/1.0a/getAccessToken"
                                   "https://api.smugmug.com/services/oauth/1.0a/authorize"
                                   :hmac-sha1))



(def REDIRECT_URI
  (let [env (.value SystemProperty/environment)
        uri (if (= env (SystemProperty$Environment$Value/Production))
             "https://photosync.net/smugmug_callback"
             "http://localhost:8080/smugmug_callback")]
    uri))


(defn save-session-misc
  [req misc]
  (let [session-key (get-in req [:session :identity])
        session (ds/find-by-key session-key)]
     (ds/save (merge session {:misc misc}))))


(defn get-session
  [req]
  (let [session-key (get-in req [:session :identity])]
    (ds/find-by-key session-key)))




(defn smugmug-redirect
  [req]
  (let [request-token (oauth/request-token consumer REDIRECT_URI)
        redirect_uri (oauth/user-approval-uri consumer (:oauth_token request-token) {:Access "Full" :Permissions "Read"})]
    (save-session-misc req {:smugmug_req_token request-token})
    (redirect redirect_uri :temporary-redirect)))

; oauth_problem=parameter_absent
;    &oauth_parameters_absent=
; oauth_consumer_key
; oauth_signature
; oauth_signature_method
; oauth_nonce
; oauth_timestamp
; ))


;http://localhost:8080/smugmug_callback?oauth_token=gxhhncvVc6RdRbgHRSXpFZF4VZxgzMvp&Access=Full&Permissions=Read&oauth_verifier=027518


; If user doesn't consent, redirect to a message
; If user does consent, complete the flow
;{:oauth_token 3tXVtxZLB6nXDqfCJRWR7TvfttsCDvSp, :oauth_token_secret vSjZstQjfCcfdHWshjkF6f5rzWR84ts3DZhPmW8nWcRZNbbLs5jnbpGhm74zpKt9}

(def SMUG_HEADERS {"User-Agent" "PhotoSync.Net-Server"})
(def SMUGMUG_USER "https://api.smugmug.com/api/v2!authuser")
(defn USER_EP [user endpoint] (str "https://api.smugmug.com/api/v2/user/" user "!" endpoint))

(defn smug-request
  [oauth uri method]
  (let [creds (oauth/credentials consumer (:access_token oauth) (:refresh_token oauth) method uri)
        resp (http/request {:method (name method) :accept :json :headers SMUG_HEADERS :url uri :query-params creds})
        rl-remain (get-in resp [:headers :X-RateLimit-Remaining])]
     (log/info (str "Calls remaining: " rl-remain))
     (:Response (parse/parse-string (:body resp) true))))


(defn update-smuguser
 [oauth]
 (let [user-details (smug-request oauth SMUGMUG_USER :GET)
       username (get-in user-details [:User :Name])
       bio-img (smug-request oauth (USER_EP username "bioimage" ) :GET)]
   (models/save-or-update :smug-user (:owner oauth)
                          (merge
                            {:owner (:owner oauth)}
                            {:bio-thumb (get-in bio-img [:BioImage :ThumbnailUrl])}
                            {:node (get-in user-details [:User :Uris :Node :Uri])}
                            (:User user-details)))))


(defn smugmug-callback
 [req]
 (let [params (:params (ring.middleware.params/params-request req))
       session (get-session req)
       session-misc (:misc session)
       googleuser-key (:googleuser-key session)
       oauth_token (:smugmug_req_token session-misc)
       oauth_verifier (get params "oauth_verifier")
       access-token-response (oauth/access-token consumer
                                                 oauth_token
                                                 oauth_verifier)
       oauth {:owner googleuser-key
              :access_token (:oauth_token access-token-response)
              :source "smugmug"
              :refresh_token (:oauth_token_secret access-token-response)
              :expires nil}]
      (models/save-or-update-oauth oauth)
      (save-session-misc req (dissoc session-misc :smugmug_req_token))
      (update-smuguser oauth)
      (redirect "/#services" :temporary-redirect)))


(defn smugmug-user
 [req]
 (let [session (get-session req)
        guk (:googleuser-key session)]
   (do
    (let [oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))]
      (update-smuguser oauth))
    (if-let [smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))]
     (response (prn-str smug))
     (response "not-found")))))


(defroutes smug-routes
 (GET "/getsmug" [] smugmug-redirect)
 (GET "/smugmug_test" [] smugmug-user)
 (GET "/smugmug_callback" [] smugmug-callback))


