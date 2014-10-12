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
package edu.emory.clir.clearnlp.component.mode.pos;

import java.io.InputStream;

import org.w3c.dom.Element;

import edu.emory.clir.clearnlp.component.state.AbstractState;
import edu.emory.clir.clearnlp.dependency.DEPNode;
import edu.emory.clir.clearnlp.feature.AbstractFeatureExtractor;
import edu.emory.clir.clearnlp.feature.common.CommonFeatureTemplate;
import edu.emory.clir.clearnlp.feature.common.CommonFeatureToken;
import edu.emory.clir.clearnlp.util.StringUtils;

/**
 * @since 3.0.0
 * @author Jinho D. Choi ({@code jinho.choi@emory.edu})
 */
public class POSFeatureExtractor extends AbstractFeatureExtractor<CommonFeatureTemplate,CommonFeatureToken,POSState>
{
	private static final long serialVersionUID = -7336596053366459297L;

	public POSFeatureExtractor(InputStream in)
	{
		super(in);
	}
	
	@Override
	protected CommonFeatureTemplate createFeatureTemplate(Element eFeature)
	{
		return new CommonFeatureTemplate(eFeature);
	}

	@Override
	protected String getFeature(CommonFeatureToken token, POSState state, DEPNode node)
	{
		boolean includeForm = state.includeForm(node);
		
		switch (token.getField())
		{
		case f : return includeForm ? node.getWordForm() : null;
		case f2: return includeForm ? node.getSimplifiedForm() : null;
		case f3: return includeForm ? state.getLowerSimplifiedWordForm(node) : null;
		case m : return includeForm ? node.getLemma() : null;
		case p : return node.getPOSTag();
		case a : return state.getAmbiguityClass(node);
		case n : return node.getNamedEntityTag();
		case d : return node.getLabel();
		case b : return getBooleanFeatureValue(token, state, node);
		case ft: return node.getFeat((String)token.getValue());
		default: return null;
		}
	}
	
	@Override
	protected String[] getFeatures(CommonFeatureToken token, POSState state, DEPNode node)
	{
		switch (token.getField())
		{
		case pf  : return StringUtils.getPrefixes(state.getLowerSimplifiedWordForm(node), (int)token.getValue());
		case sf  : return StringUtils.getSuffixes(state.getLowerSimplifiedWordForm(node), (int)token.getValue());
		case ds  : return toLabelArray(node.getDependentList());
		case ds2 : return toLabelArray(node.getGrandDependentList());
		case orth: return getOrthographicFeatures(state, node);
		default  : return null;
		}
	}
	
	private String getBooleanFeatureValue(CommonFeatureToken token, AbstractState<?> state, DEPNode node)
	{
		int field = (int)token.getValue();
		boolean b = false;
		
		switch (field)
		{
		case  0: b = state.isFirstNode(node); break;
		case  1: b = state.isLastNode(node);  break;
		default: throw new IllegalArgumentException("Unsupported feature: b"+token.getValue());
		}
		
		return b ? Integer.toString(field) : null;
	}
}
