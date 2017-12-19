(ns focal-todo.util
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
               (cb (prn-str (.getResponseText this)))))
           "POST" remote
           #js {"Content-Type" "application/edn"})))

