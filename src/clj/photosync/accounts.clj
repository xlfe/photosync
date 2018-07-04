(ns photosync.accounts
  (:require
            [clojure.tools.logging :as log]
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
            [photosync.smugmug :as smug]
            [photosync.walk :as walk]
            [oauth.client :as oauth]))

; options
; we have an oauth token but no root-node
; root-node has no data
; root-node requires more
; root-node is completed
; oauth token is invalid or expired

(defn albums-from-node
 [node]
 (filter (complement nil?) (map #(get-in % [:Uris :Album :Album]) (tree-seq :HasChildren :children node))))



(defn proc-smugmug
 [guk]
 (if-let [node (first (ds/find-by-kind :smug-node :filters [[:= :owner guk]]))]
     (if (:data node)
       (let [root-node (nippy/thaw (:data node))
             albums (albums-from-node root-node)
             album-count (count albums)
             size (apply + (map :OriginalSizes albums))
             image-count (apply + (map :ImageCount albums))]
        nil))))

     ;(let [user-details (smug/smug-request oauth SMUGMUG_USER :GET)])


         ;(hash-map :thumb (get % :bio-thumb)
         ;     :images (get % :ImageCount)
         ;     :size
         ;     :uri (get % :WebUri)
         ;     :name (get % :NickName)
         ;     :uptodate false])))])

(defn smugmug-accounts
 [guk]

 (let [tokens (ds/find-by-kind :oauth-token :filters [[:= :account-type "smugmug"] [:= :owner guk]])
       accounts (ds/find-by-kind :smug-user :filters [:= :owner guk])]
  (map #(proc-smugmug guk %) accounts)))



(defn get-valid-accounts
 [guk]
 (let [;smugmug (smugmug-accounts guk)
       tokens (ds/find-by-kind :oauth-token :filters [:= :owner guk])]
    (map #(select-keys % [:created-at :source :key]) tokens)))


