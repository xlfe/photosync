(ns photosync.util
  (:require [cljs.reader :as reader])
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
               (cb (reader/read-string (.getResponseText this)))))
           "POST" (prn-str remote)
           #js {"Content-Type" "application/edn"})))

