(ns photosync.smugmug
  (:use compojure.core)
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [clojure.walk :as cw]

            [taoensso.nippy :as nippy]
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
            [photosync.walk :as walk]
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
  ([oauth uri method]
   (smug-request oauth uri method nil))
  ([oauth uri method request-params]
   (do
    (let [creds (oauth/credentials consumer (:access_token oauth) (:refresh_token oauth) method uri request-params)
          resp (http/request {:method (name method) :accept :json :headers SMUG_HEADERS :url uri :query-params (merge creds request-params)})
          rl-remain (get-in resp [:headers :X-RateLimit-Remaining])]
     (do
       (log/info (str "Calls remaining: " rl-remain))
       (:Response (parse/parse-string (:body resp) true)))))))


(defn update-smuguser
 [oauth]
 (let [user-details (smug-request oauth SMUGMUG_USER :GET)
       username (get-in user-details [:User :Name])
       bio-img (smug-request oauth (USER_EP username "bioimage" ) :GET)]
   (models/save-or-update :smug-user [:= :owner (:owner oauth)]
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
     (response (with-out-str (pp/pprint smug)))
     (response "not-found")))))

(defn get-root-node
  [req]
  (let [session (get-session req)
        guk (:googleuser-key session)
        oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
        smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
        root-node (select-keys (:Node (smug-request oauth (walk/NODE_URI (:node smug)) :GET walk/ROOT_NODE_PARAMS)) walk/NODE_KEYS)]
    (do
      (models/save-or-update :smug-node [[:= :owner guk] [:= :smugmug (:key smug)]]
                             {
                              :smugmug (:key smug)
                              :data (nippy/freeze root-node)
                              :owner guk})
      (response (with-out-str (pp/pprint root-node))))))

(defn needs-fetching
 [node]
 (and
   (:HasChildren node)
   (nil? (:children node))))

(defn update-if-req
 [oauth node]
 (if (needs-fetching node)
   (let [children (:Node (smug-request oauth (walk/NODE_URI_CHILDREN (:Uri node)) :GET walk/SORT_PARAMS))
         nodes (map #(select-keys % walk/NODE_KEYS) children)]
    (merge node {:children nodes}))
   node))

(defn update-smugmug-nodes
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)
       oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
       smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
       start-root (nippy/thaw (:data (first (ds/find-by-kind :smug-node :filters [[:= :owner guk] [:= :smugmug (:key smug)]]))))
       updated-root (cw/postwalk (partial update-if-req oauth) start-root)]
   (do
     (models/save-or-update :smug-node [[:= :owner guk] [:= :smugmug (:key smug)]]
                              {
                               :smugmug (:key smug)
                               :data (nippy/freeze updated-root)
                               :owner guk})
     (response (with-out-str (pp/pprint updated-root))))))

(defn view-smugmug-nodes
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)
       smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
       root-node (nippy/thaw (:data (first (ds/find-by-kind :smug-node :filters [[:= :owner guk] [:= :smugmug (:key smug)]]))))]
      (response (with-out-str (pp/pprint root-node)))))



(defroutes smug-routes
 (GET "/getsmug" [] smugmug-redirect)
 (GET "/smugmug_test" [] smugmug-user)
 (GET "/get_root_node" [] get-root-node)
 (GET "/update_smug_nodes" [] update-smugmug-nodes)
 (GET "/view_smug_nodes" [] view-smugmug-nodes)
 (GET "/smugmug_callback" [] smugmug-callback))


