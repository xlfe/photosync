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
                                                 oauth_verifier)]

      (save-session-misc req (dissoc session-misc :smugmug_req_token))
      (models/save-or-update-oauth  {:owner googleuser-key
                                     :access_token (:oauth_token access-token-response)
                                     :source "smugmug"
                                     :refresh_token (:oauth_token_secret access-token-response)
                                     :expires nil})
      (redirect "/#services" :temporary-redirect)))

(def SMUGMUG_USER "https://api.smugmug.com/api/v2!authuser")

(defn smug-request
  [req uri method]
  (let [session (get-session req)
        guk (:googleuser-key session)]
    (if-let [oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))]
      (let [creds (oauth/credentials consumer (:access_token oauth) (:refresh_token oauth) method uri)]
       (:Response (parse/parse-string (:body (http/request {:method (name method) :accept :json :url uri :query-params creds})) true))))))



(defn smugmug-user
 [req]
 (let [user (smug-request req SMUGMUG_USER :GET)]
   (response (prn-str user))))


(defroutes smug-routes
 (GET "/getsmug" [] smugmug-redirect)
 (GET "/smugmug_test" [] smugmug-user)
 (GET "/smugmug_callback" [] smugmug-callback))


