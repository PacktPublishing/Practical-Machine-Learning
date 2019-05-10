// Practical Machine learning
// Deep learning - Autoencoder example 
// Chapter 11

package main.java;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.mllib.linalg.DenseVector;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;

public class AutoencoderFct {

	private Broadcast<AutoencoderParams> params;
	private JavaRDD<Vector> data;
	private double rho = 0.05;
	private double lambda = 0.001;
	private double beta = 6;

	public AutoencoderFct(Broadcast<AutoencoderParams> params, JavaRDD<Vector> data, AutoencoderConfig conf){
		this.params = params;
		this.data = data;
		rho = conf.getRho();
		lambda = conf.getLambda();
		beta = conf.getBeta();
	}
	
	public double computeTestError(){

		FirstLayerActivation firstLayerActivation = new FirstLayerActivation(params);
		JavaRDD<Vector> a2 = data.mapPartitions(firstLayerActivation); 	

		ComputeRho computeRho = new ComputeRho();
		Vector rho_h =  a2.reduce(computeRho);

		long numSamples = data.count();

		double[] rhoH = rho_h.toArray();
		double[] sparsityArray = new double[rhoH.length];

		double KL = 0;
		double t1,t2,t3,t4;
		for (int i=0;i<rhoH.length;i++){

			//rhoH might be negative
			try{
				t1 = rho * Math.log(rho/rhoH[i]*numSamples);
			}
			catch (Exception e){
				t1 = 0;
			}

			//1 case would be rhoH = 1;
			try{
				t2 = (1-rho) * Math.log((1-rho)/(1-rhoH[i]/numSamples));
			}
			catch (Exception e){
				t2 = 0;
			}

			KL += t1+t2;

			try{
				t3 = -rho/rhoH[i]*numSamples; 
			}catch (Exception e){
				t3 = 0;
			}

			try{
				t4 = (1-rho)/(1-rhoH[i]/numSamples); 
			}catch (Exception e){
				t4 = 0;
			}
			sparsityArray[i] = beta*(t3+t4);

		}

		JavaSparkContext sc = JavaSparkContext.fromSparkContext(data.context());
		Broadcast<AutoencoderComputedParams> computedBroadcast = sc.broadcast(new AutoencoderComputedParams(numSamples, sparsityArray));

		double norm1 = Vectors.norm((Vector)new DenseVector(params.getValue().getW1().toArray()),2);
		double norm2 = Vectors.norm((Vector)new DenseVector(params.getValue().getW2().toArray()),2);

		Compute compute = new Compute(params, computedBroadcast);
		AutoencoderSum  	sum     = new AutoencoderSum();


		double fctValue = data.mapPartitions(compute).reduce(sum);


		return fctValue+0.5*lambda*(norm1*norm1+norm2*norm2)+beta*KL;

	}
	
	private static class FirstLayerActivation implements FlatMapFunction<Iterator<Vector>,Vector>{


		private Broadcast<AutoencoderParams> params;

		public FirstLayerActivation(Broadcast<AutoencoderParams> params) {
			this.params = params;
		}


		public Iterable<Vector> call(Iterator<Vector> arg0) throws Exception {
			//		double[] b1V = params.getValue().getB1().toArray();
			//		DenseVector result = new DenseVector(params.getValue().getB1().toArray());
			//		BLAS.gemv(1.0, params.getValue().getW1(), (DenseVector) arg0._1, 0.0, result);
			double[] values = new double[params.getValue().getW1().numRows()];
			while(arg0.hasNext()){
				double[] newValue = AutoencoderLinAlgebra.MVM(params.getValue().getW1(), arg0.next());
				for(int i=0;i<values.length;i++){
					values[i] += AutoencoderLinAlgebra.sigmoid(newValue[i]+params.getValue().getB1().apply(i));
					//Avoid complex computation and use a Singleton class to get precomputed values
					//AutoencoderSigmoid.getInstance().getValue(values[i]);
				}
			}
			ArrayList<Vector> result = new ArrayList<Vector>();
			result.add(new DenseVector(values));
			return result;
		}

	}


	private static class ComputeRho implements Function2<Vector, Vector, Vector>{

		@Override
		public Vector call(Vector arg0,  Vector arg1) throws Exception {
			//		DenseVector result = new DenseVector(arg0._1.toArray());
			//		BLAS.axpy(1.0, arg1._1, result);
			return new DenseVector(AutoencoderLinAlgebra.VVA(arg0,arg1)); 
		}

	}

	private static class Compute implements FlatMapFunction<Iterator<Vector>,Double>{
		private Broadcast<AutoencoderParams> params;
		private Broadcast<AutoencoderComputedParams> comp;

		public Compute(Broadcast<AutoencoderParams> params,Broadcast<AutoencoderComputedParams> comp){
			this.params = params;
			this.comp = comp;
		}

		@Override
		public Iterable<Double> call( Iterator<Vector> arg0)
				throws Exception {

			double fctValue = 0;

			while(arg0.hasNext()){

				double[] x  = arg0.next().toArray();
				double[] a2A = AutoencoderLinAlgebra.MAM(params.getValue().getW1(), x);					
				for(int i=0;i<a2A.length;i++){
					a2A[i] = AutoencoderLinAlgebra.sigmoid(a2A[i]+params.getValue().getB1().apply(i));
					//Avoid complex computation and use a Singleton class to get precomputed values
					//AutoencoderSigmoid.getInstance().getValue(values[i]);

				}




				double[] delta3A = AutoencoderLinAlgebra.VAA(params.getValue().getB2(),
						AutoencoderLinAlgebra.MAM(params.getValue().getW2(),a2A)); 
				for(int i=0;i<delta3A.length;i++){
					double sig = AutoencoderLinAlgebra.sigmoid(delta3A[i]);
					//Avoid complex computation and use a Singleton class to get precomputed values
					//AutoencoderSigmoid.getInstance().getValue(values[i]);
					double val = (x[i]-sig);
					fctValue += val*val;			
				}


			};



			ArrayList<Double> result = new ArrayList<Double>();
			result.add(0.5*fctValue/comp.getValue().getNumSamples());
			return result;
		}

	}
	
	private static class AutoencoderSum implements Function2<Double,Double,Double>{

		@Override
		public Double call(Double arg0, Double arg1) throws Exception {
			// TODO Auto-generated method stub
			return arg0+arg1;
		}

	}
}
