(ns photosync.walk
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.zip :as z]
            [cheshire.core :as parse]
            [taoensso.nippy :as nippy]
            [hyperion.api :as ds]
            [photosync.model :as models]
            [photosync.util :as util]
            [oauth.client :as oauth])
  (:import (com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value)))


(defn NODE_URI
 ([nodeid]
  (NODE_URI nodeid nil))
 ([nodeid extras]
  (str "https://api.smugmug.com/api/v2/node/" nodeid extras)))

(defn NODE_URI_CHILDREN
 [nodeid]
 (NODE_URI nodeid "!children?SortDirection=Descending&SortMethod=DateModified&Type=All"))

; Each node has the following details

;children
;DateAdded	2017-11-24T04:36:06+00:00
;DateModified	2017-11-26T02:00:00+00:00
;Description	empty string
;EffectivePrivacy	Unlisted
;EffectiveSecurityType	None
;HasChildren true
;HideOwner	false
;Keywords
;Name	Card
;NodeID	***REMOVED***
;Privacy	Unlisted
;SecurityType	None
;Type	Album
;UrlName	Card
;UrlPath	/Card
;WebUri	https://***REMOVED***/Card/n-***REMOVED***

; we start at the root node and walk all the nodes breadth first
; we end up with a big nested map of all the nodes...
; any node with HasChildren == true and an empty children key is ready for CHILDREN

(defn get-zipper-from-data
 [data]
 (nippy/thaw data))



(defn complete-tree
 [tree])
