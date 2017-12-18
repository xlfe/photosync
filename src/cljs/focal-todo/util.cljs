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

(defn transit-post [url]
  (fn [{:keys [remote]} cb]
    (.send XhrIo url)))
