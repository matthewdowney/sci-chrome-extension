(ns com.mjdowney.scx.service-worker
  (:require [cljs.analyzer.api]
            [clojure.string :as string]
            [sci.core :as sci]))

(def sci-ctx (sci/init {}))

(defn balance [s]
  (reduce
    (fn [xs x]
      (case x
        \( (update xs :opens inc)
        \) (update xs :closes inc)
        xs))
    {:opens 0 :closes 0}
    s))

(def xml-escapes
  {\" "&quot;"
   \' "&apos;"
   \< "&lt;"
   \> "&gt;"
   \& "&amp;"})

(defn init []
  (js/chrome.omnibox.onInputChanged.addListener
    (fn [text suggest]
      (when (seq text)
        (let [{:keys [opens closes]} (balance text)
              suffix (if (> opens closes)
                       (apply str (repeat (- opens closes) \)))
                       "")
              suggestion (str text suffix)
              result (try
                       (sci/eval-string* sci-ctx suggestion)
                       (catch :default e (str e)))
              desc (str
                     "<match>" text "</match>"
                     "<dim> ;=> "
                     (string/escape (str result) xml-escapes)
                     "</dim>")]
          (suggest #js [#js {:content (str suggestion " ")
                             :description desc}])))))

  (println "Service worker initialized."))
