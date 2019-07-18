(ns photosync.smugmug
  (:use compojure.core)
  (:require [clj-http.client :as http]
            [clojure.pprint :as pp]
            [clojure.walk :as cw]

            [taoensso.nippy :as nippy]
            [ring.util.response :refer [redirect response]]
            [compojure.core :refer [defroutes ANY GET POST]]
            [ring.util.response :as response]
            [clojure.java.io :as io]
            [cheshire.core :as parse]
            [hyperion.api :as ds]
            [java-time :as time]
            [clojure.contrib.humanize :as human]
            [photosync.model :as models]
            [photosync.util :as util]
            [photosync.logging :as log]
            [photosync.deferred :as deferred]
            [photosync.secrets :as secrets]
            [photosync.walk :as walk]
            [oauth.client :as oauth])
  (:import (com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value)))



(def consumer (oauth/make-consumer secrets/smugmug-apikey
                                   secrets/smugmug-apisecrett
                                   "https://api.smugmug.com/services/oauth/1.0a/getRequestToken"
                                   "https://api.smugmug.com/services/oauth/1.0a/getAccessToken"
                                   "https://api.smugmug.com/services/oauth/1.0a/authorize"
                                   :hmac-sha1))

(def SMUG_HEADERS {"User-Agent" "PhotoSync.Net-Server"})
(def SMUGMUG_USER "https://api.smugmug.com/api/v2!authuser")
(defn USER_EP [user endpoint] (str "https://api.smugmug.com/api/v2/user/" user "!" endpoint))

(def REDIRECT_URI
  (if
    (=
      (.value SystemProperty/environment)
      (SystemProperty$Environment$Value/Production))
    "https://photosync.net/smugmug_callback"
    "http://localhost:8080/smugmug_callback"))


(defn smug-request
  "make a request to the SmugMug api with a user's oauth credentials"
  ([oauth uri method]
   (smug-request oauth uri method nil))
  ([oauth uri method request-params]
   (do
     (log/info (str "smug-request called for url " uri " with request params: " request-params " and oauth " (:key oauth)))
     (let [creds (oauth/credentials consumer (:access_token oauth) (:refresh_token oauth) method uri request-params)
           resp (http/request {:method (name method) :accept :json :headers SMUG_HEADERS :url uri :query-params (merge creds request-params)})
           rl-remain (get-in resp [:headers :X-RateLimit-Remaining])]
        (log/info (:status resp))
        (log/info (get (:headers resp) "Content-Type"))
        (do
            (log/info (str "Calls remaining: " rl-remain))
            (:Response (parse/parse-string (:body resp) true)))))))


(defn save-session-misc
  [req misc]
  (let [session-key (get-in req [:session :identity])
        session (ds/find-by-key session-key)]
     (ds/save (merge session {:misc misc}))))


(defn get-session
  [req]
  (let [session-key (get-in req [:session :identity])]
    (ds/find-by-key session-key)))





;;;
;;; Testing routes
;;;



(defn test-view-smugmug-nodes
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)
       ; (:owner :smug-user) is the oauth key
       smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
       root-node (nippy/thaw (:data (first (ds/find-by-kind :smug-node :filters [:= :owner (:key smug)]))))]
     (log/debug guk)
     (log/debug smug)
     ;(response (pr-str (cw/prewalk filter-nodes root-node)))))
     (response (with-out-str (pp/pprint root-node)))))
     ;(response (with-out-str (pp/pprint smug)))))
     ;(response (with-out-str (pp/pprint smug)))))
     ;(response (str na " albums, total: " (human/filesize sum)))))



(defn test-remove-smugmug-data
  [req]
  (let [session (get-session req)
        guk (:googleuser-key session)
        oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
        smug (first (ds/find-by-kind :smug-user :filters [:= :owner (:key oauth)]))
        node (first (ds/find-by-kind :smug-node :filters [:= :owner (:key smug)]))]

    (if (nil? oauth)
      (response "nothing to delete")
      (do
        (ds/delete-by-key (:key oauth))
        (ds/delete-by-key (:key smug))
        (ds/delete-by-key (:key node))
        (response "done")))))

(defn test-display-smugmug-user
  [req]
  (let [session (get-session req)
        guk (:googleuser-key session)
        oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
        smug (first (ds/find-by-kind :smug-user :filters [:= :owner (:key oauth)]))]
    (if smug
      (response (with-out-str (pp/pprint smug)))
      (response "not-found"))))


;;;
;;; OAUTH Flow
;;;

(defn oauth-smugmug-redirect
  "start the oauth flow for smugmug. Check the user doesn't already have a smugmug token"
  [req]
  (let [request-token (oauth/request-token consumer REDIRECT_URI)
        redirect_uri (oauth/user-approval-uri consumer (:oauth_token request-token) {:Access "Full" :Permissions "Read"})]
    (save-session-misc req {:smugmug_req_token request-token})
    (redirect redirect_uri :temporary-redirect)))


(defn oauth-smugmug-callback
  "user has authenticated with smugmug so save the oauth-token with their guk as the owner"
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
        oauth {:owner         googleuser-key
               :access_token  (:oauth_token access-token-response)
               :source        "smugmug"
               :refresh_token (:oauth_token_secret access-token-response)
               :expires       nil}
        oauth-key (models/save-or-update :oauth-token [[:= :owner googleuser-key] [:= :source "smugmug"]] oauth)]
    (save-session-misc req (dissoc session-misc :smugmug_req_token))
    (deferred/add! :url "/tasks/get-smugmug-user" :params {:oauth-key oauth-key})
    (redirect "/#services" :temporary-redirect)))



;;;
;;; Background tasks
;;;


(defn task-get-smugmug-user
  "Called through TaskQueue API once the user has completed the SmugMug OAUTH Flow (by smugmug-callback)
   Creates a :smug-user object which has the SmugMug user details linked to the :oauth-token object
    Requires param oauth-key which is the oauth object"
  [req]
  (log/info (str "task-get-smugmug-user called with oauth-key " (get-in req [:params "oauth-key"])))
  (if-let [oauth (ds/find-by-key (get-in req [:params "oauth-key"]))]
    (let [user-details (smug-request oauth SMUGMUG_USER :GET)
          username (get-in user-details [:User :Name])
          bio-img (smug-request oauth (USER_EP username "bioimage") :GET)
          smug-user-key (models/save-or-update
                          :smug-user
                          [:= :owner (:key oauth)]
                          (merge
                            {:owner (:key oauth)}
                            {:bio-thumb (clojure.string/replace (get-in bio-img [:BioImage :ThumbnailUrl]) "/Th/" "/L/")}
                            {:node (get-in user-details [:User :Uris :Node :Uri])}
                            (:User user-details)))]
      (log/info (str "saved smug-user for " username))
      (deferred/add! :url "/tasks/get-smugmug-root-node" :params {:smug-user-key smug-user-key})
      (response "OK"))))

(defn task-get-smugmug-root-node
  "Get root node from smugmug"
  [req]
  (log/info (str "task-get-smugmug-root-node called with smug-user-key " (get-in req [:params "smug-user-key"])))
  (if-let [smug (ds/find-by-key (get-in req [:params "smug-user-key"]))]
    (let [oauth (ds/find-by-key (:owner smug))
          root-node (select-keys (:Node (smug-request oauth (walk/NODE_URI (:node smug)) :GET walk/ROOT_NODE_PARAMS)) walk/NODE_KEYS)
          smug-node-key (models/save-or-update
                          :smug-node [:= :smugmug (:key smug)]
                          {
                           :owner (:key smug)
                           :data  (nippy/freeze root-node)})]
      (deferred/add! :url "/tasks/update-smugmug-nodes" :params {:smug-node-key smug-node-key})
      (log/info (str "saved smug-node"))
      (response "OK"))))




(defn needs-fetching
 [node]
 (and
   (:HasChildren node)
   (nil? (:children node))))

;(< (time/as (time/duration req-start (time/instant)) :seconds) 5)
(defn update-if-req
 [req-start oauth node]
 (if (and
       (needs-fetching node)
       (<= (swap! req-start inc) 1))
   (let [children (:Node (smug-request oauth (walk/NODE_URI_CHILDREN (:Uri node)) :GET walk/SORT_PARAMS))
         nodes (map #(select-keys % walk/NODE_KEYS) children)]
    (merge node {:children nodes}))
   node))


(defn get-album
  [node]
  (get-in node [:Uris :Album :Album]))

(defn get-images
 [node]
 (let [img (get-in node [:Uris :Album :Album :Uris :HighlightImage :Image])]
   {:largest_image (get-in img [:Uris :LargestImage :LargestImage :Url])
    :thumb_image   (get-in img [:ThumbnailUrl])}))

(defn albums-from-node
  [node]
  (filter (complement nil?) (map #(get-in % [:Uris :Album :Album]) (tree-seq :HasChildren :children node))))

(defn filter-nodes
 [node]
 (cond
   ((complement nil?) (get-album node)) (merge (select-keys (get-album node) walk/MIN_KEYS) (get-images node))
   (:children node) (select-keys node walk/MIN_KEYS)
   (:Type node) nil
   :else node))

(defn smugmug-root-node-summmary
  [root]
  (let [albums (albums-from-node root)]
    {
     :album-count  (count albums)
     :image-count  (apply + (map :ImageCount albums))
     :storage-used (human/filesize (apply + (map :OriginalSizes albums)))}))


(defn task-update-smugmug-nodes
  [req]
  (log/info (str "task-update-smugmug-nodes called with smug-node-key " (get-in req [:params "smug-node-key"])))
  (if-let [node (ds/find-by-key (get-in req [:params "smug-node-key"]))]
    (let [
          smug (ds/find-by-key (:owner node))
          oauth (ds/find-by-key (:owner smug))
          smug-images (:ImageCount smug)
          smug-key (:key smug)
          req-count (atom 0)]
      (if-let [data (:data node)]
        (let [start-root (nippy/thaw data)
              updated-root (cw/postwalk (partial update-if-req req-count oauth) start-root)
              node-summary (smugmug-root-node-summmary updated-root)
              proportion (format "%3f" (float (/ (:image-count node-summary) smug-images)))]
          (do
            (models/save-or-update :smug-node [:= :owner smug-key]
                                    {
                                     :data (nippy/freeze updated-root)
                                     :remaining-nodes @req-count
                                     :owner smug-key})
            (log/info (str "update-smugmug-nodes OK " proportion " - " @req-count " nodes have children to fetch." node-summary))
            (if (< 0 @req-count)
              (deferred/add! :url "/tasks/update-smugmug-nodes" :params {:smug-node-key (:key node)}))
            (response "OK")))))))






(defroutes
  smug-routes
  (GET "/getsmug" [] oauth-smugmug-redirect)
  (GET "/smugmug_callback" [] oauth-smugmug-callback)

  (GET "/test/show-smugmug-user" [] test-display-smugmug-user)
  (GET "/test/show-smugmug-nodes" [] test-view-smugmug-nodes)
  (GET "/test/remove-smugmug-data" [] test-remove-smugmug-data)

  (POST "/tasks/get-smugmug-user" [] task-get-smugmug-user)
  (POST "/tasks/get-smugmug-root-node" [] task-get-smugmug-root-node)
  (POST "/tasks/update-smugmug-nodes" [] task-update-smugmug-nodes))


