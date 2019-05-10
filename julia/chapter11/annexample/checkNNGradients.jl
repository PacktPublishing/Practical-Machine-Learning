# Practical Machine learning
# Artificial Neural Network
# Chapter 11

include("nnCostFunction.jl")
include("computeNumericalGradient.jl")
include("debugInitializeWeights.jl")

function checkNNGradients(lambda = 0)
  input_layer_size = 3
  hidden_layer_size = 5
  num_labels = 3
  m = 5

  # We generate some 'random' test data
  Theta1 = debugInitializeWeights(hidden_layer_size, input_layer_size)
  Theta2 = debugInitializeWeights(num_labels, hidden_layer_size)
  # Reusing debugInitializeWeights to generate X
  X  = debugInitializeWeights(m, input_layer_size - 1)
  y  = (1 + mod(1:m, num_labels)')'

  # Unroll parameters
  nn_params = [Theta1[:] ; Theta2[:]]

  # Short hand for cost function
  costFunc = p -> nnCostFunction(p, input_layer_size, hidden_layer_size,
                                 num_labels, X, y, lambda)
	CHECKNNGRADIENTS(lambda)
  cost, grad = costFunc(nn_params)
  numgrad = computeNumericalGradient(costFunc, nn_params)

  # Visually examine the two gradient computations.  The two columns
  # you get should be very similar.
  show([numgrad grad])
  @printf("""

    The above two columns you get should be very similar.
    (Left-Your Numerical Gradient, Right-Analytical Gradient)

  """)

  # Evaluate the norm of the difference between two solutions.
  # If you have a correct implementation, and assuming you used EPSILON = 0.0001
  # in computeNumericalGradient.m, then diff below should be less than 1e-9
  diff = norm(numgrad - grad) / norm(numgrad + grad)

  @printf("""
    If your backpropagation implementation is correct, then
    the relative difference will be small (less than 1e-9).

    Relative Difference: %g
    """, diff)

end
