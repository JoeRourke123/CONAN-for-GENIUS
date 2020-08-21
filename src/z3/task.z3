; Declare the weightings for each issue
(declare-const w1 Real)
(declare-const w2 Real)
(declare-const w3 Real)     ; This will be the continuous issue
; This check ensures that all the weightings are in the boundary (0, 1]
(assert (and (> w1 0.0) (and (> w2 0.0) (> w3 0.0))))
(assert (= (+ w1 (+ w2 w3)) 1.0))


; These will be the values required for each issue, as well as ensuring that they are greater than 0
(declare-const v11 Real)
(declare-const v12 Real)
(declare-const v13 Real)
(assert (> v11 0))
(assert (> v12 0))
(assert (> v13 0))

(declare-const v21 Real)
(declare-const v22 Real)
(declare-const v23 Real)
(assert (> v21 0))
(assert (> v22 0))
(assert (> v23 0))

; As issue 3 is continuous different values are required
; These represent the maximum and minimum integer values that can be used as input
(declare-const v3min Real)
(declare-const v3max Real)
(assert (> v3min 0.0))
(assert (> v3max v3min))

; The utilities of the minimum and maximum possible values, modelling the GENIUS integer continuous type
(declare-const v3umin Real)
(declare-const v3umax Real)
; Ensure these are in the (0, 1] boundary
(assert (and (> v3umin 0.0) (<= v3umin 1.0)))
(assert (and (> v3umax 0.0) (<= v3umax 1.0)))
(assert (< v3umax v3umin))  ; Assuming this issue is price so the lower the price the better

; These consts are required for the utility function for the continuous issue
(declare-const m Real)
(assert (= m (/ (- v3umax v3umin) (- v3max v3min))))

; A function to calculate the utility of an integer x
(define-fun w3util ((x Real)) Real (* w3 (+ (* m x) v3umax)))

; Define the possible continuous values for each bid
(declare-const v3bid1 Real)
(declare-const v3bid2 Real)
(declare-const v3bid3 Real)
(declare-const v3bid4 Real)
(declare-const v3bid5 Real)
(assert (and (>= v3bid1 v3min) (<= v3bid1 v3max)))
(assert (and (>= v3bid2 v3min) (<= v3bid2 v3max)))
(assert (and (>= v3bid3 v3min) (<= v3bid3 v3max)))
(assert (and (>= v3bid4 v3min) (<= v3bid4 v3max)))
(assert (and (>= v3bid5 v3min) (<= v3bid5 v3max)))

; Declare the bids and their utilities
(declare-const bid1 Real)
(assert (= bid1 (+ (* w1 v11) (+ (* w2 v21) (w3util v3bid1)))))

(declare-const bid2 Real)
(assert (= bid2 (+ (* w1 v12) (+ (* w2 v22) (w3util v3bid2)))))

(declare-const bid3 Real)
(assert (= bid3 (+ (* w1 v13) (+ (* w2 v23) (w3util v3bid3)))))

(declare-const bid4 Real)
(assert (= bid4 (+ (* w1 v12) (+ (* w2 v21) (w3util v3bid4)))))

(declare-const bid5 Real)
(assert (= bid5 (+ (* w1 v13) (+ (* w2 v21) (w3util v3bid5)))))

; Define the orderings of the bids
; 5 >= 4 >= 3 >= 2 >= 1
(assert (>= bid5 bid4))
(assert (>= bid4 bid3))
(assert (>= bid3 bid2))
(assert (>= bid2 bid1))

(check-sat)
(get-model)