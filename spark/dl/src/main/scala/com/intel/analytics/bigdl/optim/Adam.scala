/*
 * Copyright 2016 The BigDL Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.analytics.bigdl.optim

import com.intel.analytics.bigdl.tensor.Tensor
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric
import com.intel.analytics.bigdl.utils.{T, Table}

import scala.math._
import scala.reflect.ClassTag

class Adam[@specialized(Float, Double) T: ClassTag](implicit ev: TensorNumeric[T])
  extends OptimMethod[T] {

  /**
   * An implementation of Adam http://arxiv.org/pdf/1412.6980.pdf
   *
   * @param feval     a function that takes a single input (X), the point of a evaluation, and
   *                  returns f(X) and df/dX
   * @param parameter the initial point
   * @param config    a table with hyper-parameters for the optimizer
   *                  config("learningRate") : learning rate
   *                  config("learningRateDecay") : learning rate decay
   *                  config("beta1") : first moment coefficient
   *                  config("beta2") : second moment coefficient
   *                  config("Epsilon"): for numerical stability
   * @param state     a table describing the state of the optimizer; after each call the state
   *                  is modified
   *                  state("s") : 1st moment variables
   *                  state("r"): 2nd moment variables
   *                  state("denom"): A tmp tensor to hold the sqrt(v) + epsilon
   * @return the new x vector and the function list {fx}, evaluated before the update
   */
  override def optimize(feval: (Tensor[T]) => (T, Tensor[T]),
               parameter: Tensor[T], config: Table, state: Table): (Tensor[T], Array[T]) = {

    val _config = if (config == null) T() else config
    val _state = if (state == null) _config else state

    val lr = _config.getOrElse[Double]("learningRate", 1e-3)
    val lrd = _config.getOrElse[Double]("learningRateDecay", 0.0)
    val beta1 = _config.getOrElse[Double]("beta1", 0.9)
    val beta2 = _config.getOrElse[Double]("beta2", 0.999)
    val eps = _config.getOrElse[Double]("Epsilon", 1e-8)

    val (fx, dfdx) = feval(parameter)

    var timestep = _state.getOrElse[Int]("evalCounter", 0)

    val (_s, _r, _denom) =
      if (_state.get[Tensor[T]]("s").isDefined) {
        (_state.get[Tensor[T]]("s").get, _state.get[Tensor[T]]("r").get,
          Tensor[T]().resizeAs(dfdx).zero())
      } else {
        (Tensor[T]().resizeAs(dfdx).zero(), Tensor[T]().resizeAs(dfdx).zero(),
          Tensor[T]().resizeAs(dfdx).zero())
      }
    val clr = lr / (1 + timestep*lrd)

    timestep = timestep + 1

    _s.mul(ev.fromType[Double](beta1)).add(ev.fromType[Double](1-beta1), dfdx)
    _r.mul(ev.fromType[Double](beta2)).addcmul(ev.fromType[Double](1-beta2), dfdx, dfdx)
    _denom.resizeAs(_r).copy(_r).sqrt().add(ev.fromType[Double](eps))
    // efficiency improved upon by changing the order of computation, at expense of clarity
    val biasCorrection1 = 1 - pow(beta1, timestep)
    val biasCorrection2 = 1 - pow(beta2, timestep)
    val stepSize = clr * sqrt(biasCorrection2) / biasCorrection1
    parameter.addcdiv(ev.fromType[Double](-stepSize), _s, _denom)

    _state("evalCounter") = timestep
    _state("s") = _s
    _state("r") = _r

    (parameter, Array(fx))
  }

  override def clearHistory(state: Table): Table = {
    state.delete("s")
    state.delete("r")
  }
}
