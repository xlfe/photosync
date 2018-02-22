(ns photosync.util
  (:require
    [compassus.core :as compassus]
    [om.next :as om]
    [cljs.reader :as reader])
  (:import [goog.net XhrIo]))

(defn hidden [is-hidden]
  (if is-hidden
    #js {:display "none"}
    #js {}))

(defn pluralize [n word]
  (if (== n 1)
    word
    (str word "s")))

(defn edn-post [url]
  (fn [{:keys [remote]} cb]
    (.send XhrIo url
           (fn [e]
             (this-as this
               (println (.getStatus this))
               (if (.isSuccess this)
                 (cb (reader/read-string (.getResponseText this)))
                 (cb {:compassus.core/route :welcome})
                 )))
           "POST" (prn-str remote)
           #js {"Content-Type" "application/edn"})))

