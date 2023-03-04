(ns com.mjdowney.scx.service-worker
  (:require [cljs.analyzer.api]
            [clojure.string :as string]
            [sci.core :as sci]))

(def safe-pr-result
  "Macro for use inside the SCI execution environment which truncates infinite
  return values."
  ^:sci/macro
  (fn [_&form _&env & body]
    `(let [ret# (do ~@body)]
       (if (and (sequential? ret#) (>= (bounded-count 50 ret#) 50))
         (pr-str (concat (take 50 ret#) ['...]))
         (pr-str ret#)))))

(def sci-ctx
  "Context of SCI execution, persistent between different calls."
  (sci/init {:bindings {'safe-pr-result safe-pr-result}}))

(defn eval-with-sci
  "Eval the code in string `s` with SCI and return a string result."
  [s]
  (try
    (sci/eval-string* sci-ctx (str "(safe-pr-result" s ")"))
    (catch :default e (str e))))

(defn balance-parens
  "Return a series of ], ), or } characters necessary to balance the
  parens and brackets in the given EDN string."
  [s]
  (let [length (.-length s)]
    (letfn [(pop [xs x']
              (lazy-seq
                (if-let [x (first xs)]
                  (if (= x x')
                    (rest xs)
                    (cons x (pop (rest xs) x'))))))]
      (loop [idx 0
             closing '()]
        (if (== idx length)
          closing
          (recur
            (inc idx)
            (case (.charAt s idx)
              \( (conj closing \))
              \) (pop closing \))
              \{ (conj closing \})
              \} (pop closing \})
              \[ (conj closing \])
              \] (pop closing \])
              closing)))))))

(def xml-escapes
  {\" "&quot;"
   \' "&apos;"
   \< "&lt;"
   \> "&gt;"
   \& "&amp;"})

(defn pprint-omnibox-result [result-str]
  (str "<dim> ;=> " (string/escape result-str xml-escapes) "</dim>"))

(defn init []
  (js/chrome.omnibox.onInputChanged.addListener
    (fn [text suggest]
      (when (seq text)
        (let [suffix-chars (balance-parens text)
              suffix (apply str suffix-chars)
              to-eval (str text suffix)
              desc (str "<match>" text "</match>" suffix
                     (pprint-omnibox-result (eval-with-sci to-eval)))]
          ; extra space in :content is a hack so that the browser displays the
          ; description (incl ;=> ...) even with a literal match
          (suggest #js [#js {:content (str to-eval " ") :description desc}]))))))
