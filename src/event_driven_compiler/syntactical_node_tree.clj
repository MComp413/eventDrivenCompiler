(ns event-driven-compiler.syntactical-node-tree
  (:gen-class)
  (:require [event-driven-compiler.syntactical-node :as synt]
            [event-driven-compiler.event-queue :as q]
            [event-driven-compiler.debugger :as dbg]))


;; nós de programa principal e métodos

(def main-var-pool (ref {}))
(def function-var-pool (ref {}))
(def function-header-pool (ref {}))


; Estado do motor sintático
(def syntactical-queue (q/build-queue))

(defn get-current-token []
  ((syntactical-queue :peek)))

(defn get-next-token []
  ((syntactical-queue :pop))
  ((syntactical-queue :peek)))

; Funções de reconhecimento recursivas
; - estratégia descendente

(defn log-current-token [caller-string]
  (dbg/dbg-println (str caller-string
                        " - Type: "
                        (-> (get-current-token) :type)
                        " Content: "
                        (-> (get-current-token) :content))))

(defn match-terminal
  [& {:keys [type content caller] :or {type nil, content nil, caller ""}}]
  (let [current-token (get-current-token)]
    (cond
      (not= content nil)
      (if (= content (-> current-token :content))
        (do
          (log-current-token caller)
          (get-next-token)
          (synt/atom-synt (-> current-token :type) (-> current-token :content)))
        nil)

      (not= type nil)
      (if (= type (-> current-token :type))
        (do
          (log-current-token caller)
          (get-next-token)
          (synt/atom-synt (-> current-token :type) (-> current-token :content)))
        nil)

      :else
      nil)))

(declare slip-list)
(declare slip-expression)
(declare slip-rest-list)

(defn slip-atom []
  (or
   (match-terminal :type :integer :caller "slip-atom")
   (match-terminal :type :string :caller "slip-atom")
   (match-terminal :type :identifier :caller "slip-atom")
   (match-terminal :content "TRUE" :caller "slip-atom")
   (match-terminal :content "FALSE" :caller "slip-atom")))

; Comandos de palavra reservada

(defn aux-print [val] (println val) val)

(defn slip-if []
  (if (match-terminal :content "IF" :caller "slip-if")
    (let [cond-synt (slip-expression)]
      (if cond-synt
        (let [expr-synt (slip-expression)]
          (if expr-synt
            (aux-print (synt/if-synt cond-synt expr-synt))
            nil))
        nil))
    nil))

(defn slip-if-else []
  (if (match-terminal :content "IFELSE" :caller "slip-if-else")
    (let [cond-synt (slip-expression)]
      (if cond-synt
        (let [expr1-synt (slip-expression)]
          (if expr1-synt
            (let [expr2-synt (slip-expression)]
              (if expr2-synt
                (if (match-terminal :content ")" :caller "slip-if-else")
                  (aux-print (synt/if-else-synt cond-synt expr1-synt expr2-synt))
                  nil)
                nil))
            nil))
        nil))
    nil))

(defn slip-do-list []
  (if (match-terminal :content "DO" :caller "slip-do-list")
    (let [body-synt (slip-rest-list)]
      (if body-synt
        (aux-print (synt/do-synt body-synt))
        nil))
    nil))

(defn slip-for []
  (if (match-terminal :content "FOR" :caller "slip-for")
    (let [identif-synt (match-terminal :type :identifier :caller "slip-for")
          start-synt (if identif-synt (slip-expression) nil)
          step-synt (if start-synt (slip-expression) nil)
          stop-synt (if step-synt (slip-expression) nil)
          body-synt (if stop-synt (slip-rest-list) nil)]
      (if body-synt
        (aux-print (synt/for-synt identif-synt start-synt step-synt stop-synt body-synt))
        nil))
    nil))

(defn slip-while []
  (if (match-terminal :content "WHILE" :caller "slip-while")
    (let [cond-synt (slip-expression)
          body-synt (if cond-synt (slip-rest-list) nil)]
      (if body-synt
        (aux-print (synt/while-synt cond-synt body-synt))
        nil))
    nil))

(defn slip-rest-params []
  (let [param (match-terminal :type :identifier :caller "slip-rest-params")
        other-params (if param
                       (slip-rest-params)
                       (if (match-terminal :content ")" :caller "slip-rest-params")
                         [] nil))]
    (if other-params
      (if param
        (into [param] other-params)
        other-params)
      nil)))

(defn slip-params []
  (if (match-terminal :content "(" :caller "slip-params")
    (if (match-terminal :content "PARAMS" :caller "slip-params")
      (let [identifier-synts (slip-rest-params)]
        (if identifier-synts
          (aux-print (synt/params-synt identifier-synts))
          nil))
      nil)
    nil))


(defn slip-def []
  (if (match-terminal :content "DEF" :caller "slip-def")
    (let [name-synt (match-terminal :type :identifier :caller "slip-def")
          params-synt (if name-synt (slip-params) nil)
          body-synt (if params-synt (slip-rest-list) nil)]
      (if body-synt
        (aux-print (synt/func-synt name-synt params-synt body-synt))
        nil))
    nil))

(defn slip-set []
  (if (match-terminal :content "SET" :caller "slip-set")
    (let [identif-synt (match-terminal :type :identifier :caller "slip-set")
          value-synt (if identif-synt (slip-expression) nil)]
      (if value-synt
        (if (match-terminal :content ")" :caller "slip-set")
          (aux-print (synt/set-synt identif-synt value-synt))
          nil)
        nil))
    nil))

(defn slip-vector []
  (if (match-terminal :content "VECTOR" :caller "slip-vector")
    (let [name-synt (match-terminal :type :identifier :caller "slip-vector")
          length-synt (if name-synt (match-terminal :type :integer :caller "slip-vector") nil)]
      (if length-synt
        (if (match-terminal :content ")" :caller "slip-vector")
          (aux-print (synt/vector-synt name-synt length-synt))
          nil)
        nil))
    nil))

(defn slip-len []
  (if (match-terminal :content "LEN" :caller "slip-len")
    (let [name-synt (match-terminal :type :identifier :caller "slip-len")]
      (if name-synt
        (if (match-terminal :content ")" :caller "slip-len")
          (aux-print (synt/len-synt name-synt))
          nil)
        nil))
    nil))

(defn slip-vec-get []
  (if (match-terminal :content "VECGET" :caller "slip-vec-get")
    (let [name-synt (match-terminal :type :identifier :caller "slip-vec-get")
          index-synt (if name-synt (slip-expression) nil)]
      (if index-synt
        (if (match-terminal :content ")" :caller "slip-vec-get")
          (aux-print (synt/vec-get-synt name-synt index-synt))
          nil)
        nil))
    nil))

(defn slip-vec-set []
  (if (match-terminal :content "VECSET" :caller "slip-vec-set")
    (let [name-synt (match-terminal :type :identifier :caller "slip-vec-set")
          index-synt (if name-synt (slip-expression) nil)
          value-synt (if index-synt (slip-expression) nil)]
      (if value-synt
        (if (match-terminal :content ")" :caller "slip-vec-set")
          (aux-print (synt/vec-set-synt name-synt index-synt value-synt))
          nil)
        nil))
    nil))

(defn slip-label []
  (if (match-terminal :content "#" :caller "slip-label")
    (let [name-synt (match-terminal :type :identifier :caller "slip-label")
          expr-synt (if name-synt (slip-expression) nil)]
      (if expr-synt
        (if (match-terminal :content ")" :caller "slip-label")
          (aux-print (synt/label-synt name-synt expr-synt))
          nil)
        nil))
    nil))

(defn slip-go-to []
  (if (match-terminal :content "GOTO" :caller "slip-go-to")
    (let [name-synt (match-terminal :type :identifier :caller "slip-go-to")]
      (if name-synt
        (if (match-terminal :content ")" :caller "slip-go-to")
          (aux-print (synt/goto-synt name-synt))
          nil)
        nil))
    nil))

(defn slip-if-go-to []
  (if (match-terminal :content "IFGOTO" :caller "slip-if-go-to")
    (let [cond-synt (slip-expression)
          name-synt (if cond-synt (match-terminal :type :identifier :caller "slip-if-go-to") nil)]
      (if name-synt
        (if (match-terminal :content ")" :caller "slip-if-go-to")
          (aux-print (synt/if-goto-synt cond-synt name-synt))
          nil)
        nil))
    nil))

(defn slip-print []
  (if (match-terminal :content "PRINT" :caller "slip-print")
    (let [value-synt (slip-expression)]
      (if value-synt
        (if (match-terminal :content ")" :caller "slip-print")
          (aux-print (synt/print-synt value-synt))
          nil)
        nil))
    nil))

(defn slip-read []
  (if (match-terminal :content "READ" :caller "slip-read")
    (if (match-terminal :content ")" :caller "slip-read")
      (aux-print (synt/read-synt))
      nil)
    nil))

(defn slip-keyword-call []
  (or (slip-if)
      (slip-if-else)
      (slip-do-list)
      (slip-for)
      (slip-while)
      (slip-params)
      (slip-def)
      (slip-set)
      (slip-vector)
      (slip-len)
      (slip-vec-get)
      (slip-vec-set)
      (slip-label)
      (slip-go-to)
      (slip-if-go-to)
      (slip-print)
      (slip-read)
      nil))

; Aritmética

(defn slip-add []
  (let [operator-synt (match-terminal :content "+" :caller "slip-add")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/arithm-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-subtract []
  (let [operator-synt (match-terminal :content "-" :caller "slip-subtract")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/arithm-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-multiply []
  (let [operator-synt (match-terminal :content "*" :caller "slip-multiply")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/arithm-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-divide []
  (let [operator-synt (match-terminal :content "/" :caller "slip-divide")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/arithm-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-remainder []
  (let [operator-synt (match-terminal :content "%" :caller "slip-remainder")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/arithm-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))

(defn slip-arithmetic []
  (or (slip-add) (slip-subtract) (slip-multiply) (slip-divide) (slip-remainder)))

; Comparação

(defn slip-equals []
  (let [operator-synt (match-terminal :content "=" :caller "slip-equals")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-not-equals []
  (let [operator-synt (match-terminal :content "!=" :caller "slip-not-equals")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-greater []
  (let [operator-synt (match-terminal :content ">" :caller "slip-greater")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-lesser []
  (let [operator-synt (match-terminal :content "<" :caller "slip-lesser")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-greater-equal []
  (let [operator-synt (match-terminal :content ">=" :caller "slip-greater-equal")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-lesser-equal []
  (let [operator-synt (match-terminal :content "<=" :caller "slip-lesser-equal")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/comparison-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))

(defn slip-comparison []
  (or (slip-equals) (slip-not-equals) (slip-greater) (slip-lesser) (slip-greater-equal) (slip-lesser-equal)))

; Lógica

(defn slip-and []
  (let [operator-synt (match-terminal :content "&&" :caller "slip-and")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/binary-logic-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-or []
  (let [operator-synt (match-terminal :content "||" :caller "slip-or")
        first-operand-synt (if operator-synt (slip-expression) nil)
        second-operand-synt (if first-operand-synt (slip-expression) nil)]
    (if second-operand-synt
      (if (match-terminal :content ")" :caller "rest-binary")
        (aux-print (synt/binary-logic-synt operator-synt first-operand-synt second-operand-synt))
        nil)
      nil)))
(defn slip-not []
  (let [operator-synt (match-terminal :content "!" :caller "slip-not")
        operand-synt (if operator-synt (slip-expression) nil)]
    (if operand-synt
      (if (match-terminal :content ")" :caller "rest-unary")
        (aux-print (synt/unary-logic-synt operator-synt operand-synt))
        nil)
      nil)))


(defn slip-logical []
  (or (slip-and) (slip-or) (slip-not)))

; Expressão genérica da linguagem

(defn slip-expression []
  (let [atom-syn-token (slip-atom)]
    (if atom-syn-token
      atom-syn-token
      (slip-list))))

(defn slip-rest-list []
  (let [expr-synt (slip-expression)
        other-exprs (if expr-synt
                      (slip-rest-list)
                      (if (match-terminal :content ")" :caller "slip-rest-list")
                        [] nil))]
    (if other-exprs
      (if expr-synt
        (into [expr-synt] other-exprs)
        other-exprs)
      nil)))

(defn slip-call-list []
  (let [name-synt (match-terminal :type :identifier :caller "slip-head-list")
        args-synt (if name-synt (slip-rest-list) nil)]
    (if args-synt
      (aux-print (synt/call-synt name-synt args-synt))
      nil)))

(defn slip-list []
  (if (match-terminal :content "(" :caller "slip-list")

    (or (slip-keyword-call)
        (slip-arithmetic)
        (slip-logical)
        (slip-comparison)
        (slip-call-list)
        nil)
    nil))

(defn slip-program-end []
  (nil? (get-current-token)))

(defn slip-rest-program []
  (if (slip-expression)
    (slip-rest-program)
    (slip-program-end)))

(defn slip-program []
  (if (slip-expression)
    (slip-rest-program)
    false))


; Motor sintático
(defn init-syntactical-engine [lexical-token-queue]
  (apply (syntactical-queue :push) lexical-token-queue))

(defn run-syntactical-engine [lexical-token-queue]
  (init-syntactical-engine lexical-token-queue)
  (slip-program))
