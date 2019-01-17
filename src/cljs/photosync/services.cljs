(ns photosync.services
  (:require
            [cljs-material-ui.core :as ui]
            [cljs-material-ui.icons :as ic]
            [clojure.contrib.humanize :as humanize]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(def service-names {"google" "Google"
                    "smugmug" "SmugMug"})


(defui ^:once Service
  static om/Ident
  (ident [this {:keys [key]}]
    [:service/by-key key])

  static om/IQuery
  (query [this]
    [:key :created-at :source])

  Object
  (render [this]
    (let [{:keys [key created-at source]} (om/props this)]
      (ui/card
        (ui/card-header {:title (get service-names source)})
        (ui/card-media {:overlay (ui/card-title {:title "Relax" :subtitle "We're here to help"})})
                       ;(dom/img #js {:src yashica}))
        (ui/card-title
          {:subtitle (str created-at)
           :title (str "Account linked " (humanize/datetime created-at))})
        (ui/card-actions nil
                                       (ui/flat-button
                                         {:onClick (fn [_] (om/transact! this `[(services/delete {:key ~key} :services/list)]))
                                          :label "Unlink service"}))))))


(def service (om/factory Service {:keyfn :key}))


