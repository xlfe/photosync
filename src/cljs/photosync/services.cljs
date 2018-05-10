(ns photosync.services
  (:require
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))


(defui ^:once Service
  static om/Ident
  (ident [this {:keys [key]}]
    [:services/by-id key])

  static om/IQuery
  (query [this]
    '[:key])

  Object
  (render [this]
    (let [props (om/props this)]
      (ui/card
        (ui/card-header {:key 0 :title (:source props)})
        (ui/card-text {:key 1} (str "Service linked: " (:created-at props)))))))

(def service (om/factory Service {:keyfn :key}))

