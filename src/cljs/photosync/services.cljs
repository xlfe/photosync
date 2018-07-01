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
    [:service/by-key key])

  static om/IQuery
  (query [this]
    [:key :created-at :source])

  Object
  (render [this]
    (let [{:keys [key created-at source]} (om/props this)]
      (ui/card
        (ui/card-header {:key 0 :title source})
        (ui/card-text {}
                      (str "Service linked: " created-at)
                      (ic/action-delete { :key 2
                                          :style {
                                                  :cursor "pointer"
                                                  :float "right"}
                                          :onClick (fn [_] (om/transact! this `[
                                                                                (services/delete {:key ~key})
                                                                                :services/list]))}))))))

(def service (om/factory Service {:keyfn :key}))


