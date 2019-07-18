(ns photosync.walk
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.zip :as z]
            [cheshire.core :as parse]
            [taoensso.nippy :as nippy]
            [hyperion.api :as ds]
            [photosync.model :as models]
            [photosync.util :as util]
            [oauth.client :as oauth]))


(defn NODE_URI
 ([nodeid]
  (NODE_URI nodeid nil))
 ([nodeid extras]
  (str "https://api.smugmug.com" nodeid (if extras (str "!" extras)))))

(defn NODE_URI_CHILDREN
 [nodeid]
 (NODE_URI nodeid "children"))

(def expand-params
 ;(http/url-encode-illegal-characters
   (parse/encode
    {
     :filteruri ["Album"]
     :expand {
              :Album  {
                       :filteruri ["HighlightImage"]
                       :expand {
                                :HighlightImage {
                                                 :filteruri ["LargestImage"]
                                                 :expand    {
                                                             :LargestImage {
                                                                              :filter ["Url"]}}}}}}}))










(def ROOT_NODE_PARAMS {:_config (parse/encode {:filteruri []})})
(def SORT_PARAMS {:SortDirection "Descending" :SortMethod "DateModified" :Type "All" :count 100000 :_expandmethod "inline" :_config expand-params})

; We keep the following details
(def NODE_KEYS
 [:DateAdded  ; 2017-11-24T04:36:06+00:00
  :DateModified  ; 2017-11-26T02:00:00+00:00
  :Description  ; empty string
  :EffectivePrivacy  ; Unlisted
  :EffectiveSecurityType  ; None
  :HasChildren  ; true
  :HideOwner  ; false
  :Keywords ;
  :Name  ; Card
  :NodeID  ; Jm9VGh
  :Privacy  ;Unlisted
  :SecurityType  ;None
  :Type  ; Album
  :UrlName ; Card
  :Uri ; uri
  :Uris
  :UrlPath ; /Card
  :WebUri])  ;https://url

(def MIN_KEYS
  [
   :Name
   :NodeID
   :OriginalSizes
   :WebUri
   :ImageCount
   :children])


; we start at the root node and walk all the nodes breadth first
; we end up with a big nested map of all the nodes...
; any node with HasChildren == true and an empty children key is ready for CHILDREN

; Will need to reference against :-
;UserDeletedAlbums
;/api/v2/user/uname!deletedalbums
;UserDeletedFolders
;/api/v2/user/uname!deletedfolders
;UserDeletedPages
;/api/v2/user/uname!deletedpages

(defn get-zipper-from-data
 [data]
 (nippy/thaw data))





