/**
 * Copyright 2014, Emory University
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
package edu.emory.clir.clearnlp.classification.trainer;

import edu.emory.clir.clearnlp.classification.instance.IntInstance;
import edu.emory.clir.clearnlp.classification.model.SparseModel;
import edu.emory.clir.clearnlp.classification.model.StringModel;
import edu.emory.clir.clearnlp.classification.vector.SparseFeatureVector;
import edu.emory.clir.clearnlp.util.DSUtils;
import edu.emory.clir.clearnlp.util.MathUtils;

/**
 * AdaGrad algorithm using hinge loss.
 * @since 3.0.0
 * @author Jinho D. Choi ({@code jinho.choi@emory.edu})
 */
public class AdaGradSVM extends AbstractAdaGrad
{
	/**
	 * @param alpha the learning rate.
	 * @param rho the smoothing denominator.
	 */
	public AdaGradSVM(SparseModel model, boolean average, double alpha, double rho)
	{
		super(model, average, alpha, rho);
	}
	
	/**
	 * @param alpha the learning rate.
	 * @param rho the smoothing denominator.
	 */
	public AdaGradSVM(StringModel model, int labelCutoff, int featureCutoff, boolean average, double alpha, double rho)
	{
		super(model, labelCutoff, featureCutoff, average, alpha, rho);
	}
	
	@Override
	protected boolean update(IntInstance instance, int averageCount)
	{
		int bestLabel = getBestLabel(instance);
		
		if (!instance.isLabel(bestLabel))
		{
			updateGradients(instance, instance.getLabel(), bestLabel);
			updateWeights  (instance, instance.getLabel(), bestLabel, averageCount);
			return true;
		}
		
		return false;
	}
	
	private int getBestLabel(IntInstance instance)
	{
		double[] scores = w_vector.getScores(instance.getFeatureVector());
		scores[instance.getLabel()] -= 1d;
		return DSUtils.maxIndex(scores);
	}
	
	private void updateGradients(IntInstance instance, int yp, int yn)
	{
		SparseFeatureVector x = instance.getFeatureVector();
		int i, xi, len = x.size();
		double vi;
		
		for (i=0; i<len; i++)
		{
			xi = x.getIndex(i);
			vi = MathUtils.sq(x.getWeight(i));
			
			if (w_vector.isBinaryLabel())
			{
				d_gradients[xi] += vi;
			}
			else
			{
				d_gradients[w_vector.getWeightIndex(yp, xi)] += vi;
				d_gradients[w_vector.getWeightIndex(yn, xi)] += vi;
			}
		}
	}
	
	private void updateWeights(IntInstance instance, int yp, int yn, int averageCount)
	{
		SparseFeatureVector x = instance.getFeatureVector();
		int i, xi, len = x.size();
		double vi;
		
		for (i=0; i<len; i++)
		{
			xi = x.getIndex(i);
			vi = x.getWeight(i);
			
			if (w_vector.isBinaryLabel())
			{
				if (yp == 1) vi *= -1;
				updateWeight(xi, vi, averageCount);
			}
			else
			{
				updateWeight(w_vector.getWeightIndex(yp, xi),  vi, averageCount);
				updateWeight(w_vector.getWeightIndex(yn, xi), -vi, averageCount);
			}
		}
	}
	
	@Override
	public String trainerInfo()
	{
		return getTrainerInfo("SVM");
	}
}