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
            [java-time :as time]
            [clojure.contrib.humanize :as human]
            [photosync.model :as models]
            [photosync.util :as util]
            [photosync.deferred :as deferred]
            [photosync.walk :as walk]
            [oauth.client :as oauth])
  (:import (com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value)))



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
  "start the oauth flow for smugmug. Check the user doesn't already have a smugmug token"
  [req]
  (let [request-token (oauth/request-token consumer REDIRECT_URI)
        redirect_uri (oauth/user-approval-uri consumer (:oauth_token request-token) {:Access "Full" :Permissions "Read"})]
    (save-session-misc req {:smugmug_req_token request-token})
    (redirect redirect_uri :temporary-redirect)))


(def SMUG_HEADERS {"User-Agent" "PhotoSync.Net-Server"})
(def SMUGMUG_USER "https://api.smugmug.com/api/v2!authuser")
(defn USER_EP [user endpoint] (str "https://api.smugmug.com/api/v2/user/" user "!" endpoint))

(defn smug-request
  ([oauth uri method]
   (smug-request oauth uri method nil))
  ([oauth uri method request-params]
   (do
    (log/info uri request-params)
    (let [creds (oauth/credentials consumer (:access_token oauth) (:refresh_token oauth) method uri request-params)
          resp (http/request {:method (name method) :accept :json :headers SMUG_HEADERS :url uri :query-params (merge creds request-params)})
          rl-remain (get-in resp [:headers :X-RateLimit-Remaining])]
     (log/info (:status resp))
     (log/info (get (:headers resp) "Content-Type"))
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
                            {:bio-thumb (clojure.string/replace (get-in bio-img [:BioImage :ThumbnailUrl]) "/Th/" "/L/")}
                            {:node (get-in user-details [:User :Uris :Node :Uri])}
                            (:User user-details)))))

(defn smugmug-remove
  "remove all smugmug nodes based on google-user-key"
  [guk]
  (let [tokens (ds/find-by-kind :oauth-token :filters [[:= :owner guk] [:= :source "smugmug"]])
        users (concat (map #(ds/find-by-kind :smug-user :filters [:= :owner (:key %)]) tokens))
        nodes (concat (map #(ds/find-by-kind :smug-node :filters [:= :owner (:key %)]) tokens))]
    (str (count tokens) " tokens, " (count users) " users and " (count nodes) " nodes found")))



(defn smugmug-callback
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
       oauth {:owner googleuser-key
              :access_token (:oauth_token access-token-response)
              :source "smugmug"
              :refresh_token (:oauth_token_secret access-token-response)
              :expires nil}]
      (models/save-or-update :oauth-token [[:= :owner googleuser-key] [:= :source "smugmug"]] oauth)
      (save-session-misc req (dissoc session-misc :smugmug_req_token))
      (update-smuguser oauth)
      (redirect "/#services" :temporary-redirect)))


(defn smugmug-user
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)]
   (do
    ;(let [oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))]
    ;  (update-smuguser oauth)
    ;(if-let [smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))])
    (if-let [smug (smugmug-remove guk)]
     (response (with-out-str (pp/pprint smug)))
     (response "not-found")))))

(defn get-root-node
  [req]
  (let [session (get-session req)
        guk (:googleuser-key session)
        oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
        smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
        root-node (select-keys (:Node (smug-request oauth (walk/NODE_URI (:node smug)) :GET walk/ROOT_NODE_PARAMS)) walk/NODE_KEYS)]
    (let
        [saved (models/save-or-update :smug-node [:= :smugmug (:key smug)]
                                      {
                                       :owner (:key smug)
                                       :data (nippy/freeze root-node)})]
      (response (with-out-str (pp/pprint root-node))))))

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

(defn update-smugmug-nodes
  [req]
  (let [session (get-session req)
        guk (:googleuser-key session)
        oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
        smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
        smug-images (:ImageCount smug)
        smug-key (:key smug)
        req-count (atom 0)
        node (first (ds/find-by-kind :smug-node :filters [:= :owner smug-key]))]
    (if-let [data (:data node)]
      (let [start-root (nippy/thaw data)
            updated-root (cw/postwalk (partial update-if-req req-count oauth) start-root)
            albums (albums-from-node updated-root)
            na (count albums)
            sum (human/filesize (apply + (map :OriginalSizes albums)))
            photo-count (apply + (map :ImageCount albums))
            proportion (format "%3f" (float (/ photo-count smug-images)))]
        (do
          (models/save-or-update :smug-node [:= :owner smug-key]
                                  {}
                                   :data (nippy/freeze updated-root)
                                   :remaining-nodes @req-count
                                   :owner smug-key)
          (response (str "OK " proportion " - " @req-count
                         " nodes have children to fetch. Info for "
                         na " albums fetched (with a total storage usage of "
                         sum ") and " photo-count " photos."))))
      (response (with-out-str (pp/pprint node))))))

(defn update-smugmug-nodes
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)
       oauth (first (ds/find-by-kind :oauth-token :filters [[:= :source "smugmug"] [:= :owner guk]]))
       smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
       smug-images (:ImageCount smug)
       smug-key (:key smug)
       req-count (atom 0)
       node (first (ds/find-by-kind :smug-node :filters [:= :owner smug-key]))]
   (if-let [data (:data node)]
     (let [start-root (nippy/thaw data)
           updated-root (cw/postwalk (partial update-if-req req-count oauth) start-root)
           albums (albums-from-node updated-root)
           na (count albums)
           sum (human/filesize (apply + (map :OriginalSizes albums)))
           photo-count (apply + (map :ImageCount albums))
           proportion (format "%3f" (float (/ photo-count smug-images)))]
       (do
         (models/save-or-update :smug-node [:= :owner smug-key]
                                   {
                                    :data (nippy/freeze updated-root)
                                    :remaining-nodes @req-count
                                    :owner smug-key})
         (response (str "OK " proportion " - " @req-count
                        " nodes have children to fetch. Info for "
                        na " albums fetched (with a total storage usage of "
                        sum ") and " photo-count " photos."))))
     (response (with-out-str (pp/pprint node))))))


(defn view-smugmug-nodes
 [req]
 (let [session (get-session req)
       guk (:googleuser-key session)
       smug (first (ds/find-by-kind :smug-user :filters [:= :owner guk]))
       root-node (nippy/thaw (:data (first (ds/find-by-kind :smug-node :filters [:= :owner (:key smug)]))))]
     (log/debug guk)
     (log/debug smug)
     ;(response (pr-str (cw/prewalk filter-nodes root-node)))))
     ;(response (with-out-str (pp/pprint root-node)))))
     (response (with-out-str (pp/pprint smug)))))
     ;(response (with-out-str (pp/pprint smug)))))
     ;(response (str na " albums, total: " (human/filesize sum)))))



(defroutes smug-routes
 (GET "/getsmug" [] smugmug-redirect)
 (GET "/smugmug_test" [] smugmug-user)
 (GET "/get_root_node" [] get-root-node)
 (GET "/update_smug_nodes" [] update-smugmug-nodes)
 (GET "/view_smug_nodes" [] view-smugmug-nodes)
 (GET "/smugmug_callback" [] smugmug-callback))


