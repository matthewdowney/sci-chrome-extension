(ns com.mjdowney.scx.sci
  (:require [cljs.analyzer.api]
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
    (sci/eval-string* sci-ctx (str "(safe-pr-result " s " )"))
    (catch :default e (str e))))
