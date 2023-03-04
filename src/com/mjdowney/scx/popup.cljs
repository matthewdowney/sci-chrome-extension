(ns com.mjdowney.scx.popup)

(defn init []
  (.addEventListener (.getElementById js/document "some-button") "click"
    (fn [_event]
      (.log js/console "Button clicked")
      (js/chrome.notifications.create
        #js {:type     "basic"
             :iconUrl  "images/icon16.png"
             :title    "Button"
             :message  "Clicked"
             :buttons  #js [#js {:title "Some _other_ button"}]
             :priority 0})
      #_(.close js/window))))
