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
package edu.emory.clir.clearnlp.component.mode.dep.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.emory.clir.clearnlp.classification.prediction.StringPrediction;
import edu.emory.clir.clearnlp.collection.list.IntArrayList;
import edu.emory.clir.clearnlp.collection.stack.IntPStack;
import edu.emory.clir.clearnlp.component.CFlag;
import edu.emory.clir.clearnlp.component.mode.dep.DEPLabel;
import edu.emory.clir.clearnlp.component.mode.dep.DEPTransition;
import edu.emory.clir.clearnlp.component.mode.dep.merge.DEPMerge;
import edu.emory.clir.clearnlp.component.state.AbstractState;
import edu.emory.clir.clearnlp.dependency.DEPLib;
import edu.emory.clir.clearnlp.dependency.DEPNode;
import edu.emory.clir.clearnlp.dependency.DEPTree;
import edu.emory.clir.clearnlp.feature.AbstractFeatureToken;
import edu.emory.clir.clearnlp.util.DSUtils;
import edu.emory.clir.clearnlp.util.MathUtils;
import edu.emory.clir.clearnlp.util.arc.DEPArc;
import edu.emory.clir.clearnlp.util.constant.StringConst;

/**
 * @since 3.0.0
 * @author Jinho D. Choi ({@code jinho.choi@emory.edu})
 */
public class DEPState extends AbstractState<DEPArc,DEPLabel> implements DEPTransition
{
	static public final int IS_ROOT   = 0;
	static public final int IS_DESC   = 1;
	static public final int NO_HEAD   = 2;
	static public final int LEFT_ARC  = 3;
	static public final int RIGHT_ARC = 4;
	
	private IntPStack i_stack;
	private IntPStack i_inter;
	private int       i_input;
	
	private List<DEPBranch> l_branches;
	private Set<String>     s_snapshots;
	private DEPMerge        d_merge;
	private boolean         save_branch;
	private int             beam_index;
	private int             beam_size;
	static public int n_trans1 = 0, n_trans2 = 0;
//	====================================== Initialization ======================================
	
	public DEPState()
	{
		super();
	}
	
	public DEPState(DEPTree tree, CFlag flag, int beamSize)
	{
		super(tree, flag);
		init(beamSize);
	}
	
	private void init(int beamSize)
	{
		i_stack = new IntPStack(t_size);
		i_inter = new IntPStack();
		i_input = 0;
		shift();
		
		l_branches  = new ArrayList<>();
		s_snapshots = new HashSet<>();
		d_merge     = new DEPMerge(d_tree);
		beam_size   = beamSize;
		save_branch = true;
	}

//	====================================== LABEL ======================================

	@Override
	protected void initOracle()
	{
		g_oracle = d_tree.getHeads();
 		d_tree.clearDependencies();
	}
	
	@Override
	public void resetOracle()
	{
		d_tree.setHeads(g_oracle);
	}
	
	@Override
	public DEPLabel getGoldLabel()
	{
		int     stack = getStackID();
		DEPNode input = getInput();
		DEPArc  oracle;
		String  list;
		
		oracle = getOracle(stack);
		
		if (oracle.getNode() == input)
		{
			list = isGoldReduce(true) ? LIST_REDUCE : LIST_PASS;
			return new DEPLabel(ARC_LEFT, list, oracle.getLabel());
		}
					
		oracle = getOracle(i_input);
		
		if (oracle.getNode() == getNode(stack))
		{
			list = isGoldShift() ? LIST_SHIFT : LIST_PASS;
			return new DEPLabel(ARC_RIGHT, list, oracle.getLabel());
		}
		
		if      (isGoldShift())			list = LIST_SHIFT;
		else if (isGoldReduce(false))	list = LIST_REDUCE;
		else							list = LIST_PASS;
		
		return new DEPLabel(ARC_NO, list, StringConst.EMPTY);
	}
	
	/** Called by {@link #getGoldLabel()}. */
	private boolean isGoldShift()
	{
		// if head(input) < stack
		int stack = getStackID();
		
		if (getOracle(i_input).getNode().getID() < stack)
			return false;
		
		// if child(input) < stack
		DEPNode input = getInput();
		int i = 1;

		while ((stack = i_stack.peek(i++)) >= 0)
		{
			if (getOracle(stack).getNode() == input)
				return false;
		}
		
		return true;
	}
	
	/** Called by {@link #getGoldLabel()}. */
	private boolean isGoldReduce(boolean hasHead)
	{
		// if stack has no head
		DEPNode stack = getStack();
		
		if (!hasHead && !stack.hasHead())
			return false;
		
		// if child(input) > stack 
		for (int i=i_input+1; i<t_size; i++)
		{
			if (getOracle(i).getNode() == stack)
				return false;
		}
		
		return true;
	}
	
	public int[][] initLabelIndices(String[] labels)
	{
		int i, size = labels.length;
		DEPLabel label;
		
		IntArrayList isRoot   = new IntArrayList();
		IntArrayList isDesc   = new IntArrayList();
		IntArrayList noHead   = new IntArrayList();
		IntArrayList leftArc  = new IntArrayList();
		IntArrayList rightArc = new IntArrayList();
		
		for (i=0; i<size; i++)
		{
			label = new DEPLabel(labels[i]);
			
			if (label.isList(LIST_SHIFT))
				isRoot.add(i);
			
			if (label.isArc(ARC_NO))
				isDesc.add(i);
			else if (label.isArc(ARC_LEFT))
				leftArc.add(i);
			else if (label.isArc(ARC_RIGHT))
				rightArc.add(i);
			
			if (!(label.isArc(ARC_NO) && label.isList(LIST_REDUCE)))
				noHead.add(i);
		}
		
		int[][] indices = new int[5][];
		
		initLabelIndices(indices, isRoot  , IS_ROOT);
		initLabelIndices(indices, isDesc  , IS_DESC);
		initLabelIndices(indices, noHead  , NO_HEAD);
		initLabelIndices(indices, leftArc , LEFT_ARC);
		initLabelIndices(indices, rightArc, RIGHT_ARC);
		
		return indices;
	}
	
	private void initLabelIndices(int[][] indices, IntArrayList list, int index)
	{
		indices[index] = list.toArray();
		Arrays.sort(indices[index]);
	}
	
	public int[] getLabelIndices(int[][] indices)
	{
		DEPNode stack = getStack();
		DEPNode input = getInput();
		
		if (stack.getID() == DEPLib.ROOT_ID)
			return indices[IS_ROOT];
		else if (stack.isDependentOf(input) || input.isDependentOf(stack))
			return indices[IS_DESC];
		else if (!stack.hasHead())
			return indices[NO_HEAD];
		else
			return null;
	}
	
//	====================================== NODE ======================================
	
	@Override
	public DEPNode getNode(AbstractFeatureToken token)
	{
		DEPNode node = null;
		int index;
		
		switch (token.getSource())
		{
		case i:
			index = getStackID() + token.getOffset();
			if (index < i_input) node = getNode(index); break;
		case j:
			index = i_input + token.getOffset();
			if (index > getStackID()) node = getNode(index); break;
		case k:
			index = (token.getOffset() <= 0) ? i_stack.peek(-token.getOffset()) : i_inter.peek(token.getOffset()-1);
			node = getNode(index); break;
		}
		
		return getNodeRelation(token, node);
	}
	
	public int getStackID()
	{
		return i_stack.peek();
	}
	
	public int getInputID()
	{
		return i_input;
	}
	
	public DEPNode getStack()
	{
		return getNode(getStackID());
	}
	
	public DEPNode getInput()
	{
		return getNode(i_input);
	}
	
	public void reset(int stackID, int inputID)
	{
		i_stack.clear();
		i_inter.clear();
		i_stack.push(stackID);
		i_input = inputID;
	}
	
//	====================================== TRANSITION ======================================
	
	@Override
	public boolean isTerminate()
	{
		if (beam_size > 1)
		{
			String snapshot = getSnapshot();
			if (!save_branch && s_snapshots.contains(snapshot)) return true;
			s_snapshots.add(snapshot);
		}
		
		return i_input >= t_size;
	}
	
	@Override
	public void next(DEPLabel label)
	{
//		saveState(label);
		DEPNode stack = getStack();
		DEPNode input = getInput();
		
		if (label.isArc(ARC_LEFT))
		{
			if (beam_size > 1) addEdge(stack, input, label);
			stack.setHead(input, label.getDeprel());
			if (label.isList(LIST_REDUCE)) reduce();
			else pass();
		}
		else if (label.isArc(ARC_RIGHT))
		{
			if (beam_size > 1) addEdge(input, stack, label);
			input.setHead(stack, label.getDeprel());
			if (label.isList(LIST_SHIFT)) shift();
			else pass();
		}
		else
		{
			if (label.isList(LIST_SHIFT)) shift();
			else if (label.isList(LIST_REDUCE) && stack.hasHead()) reduce();
			else pass();
		}
		
		if (save_branch)	n_trans1++;
		else				n_trans2++;
	}
	
	private void addEdge(DEPNode node, DEPNode head, DEPLabel label)
	{
		double d = label.getScore();
		if (save_branch) d += 1;
		d_merge.addEdge(node, head, label.getDeprel(), d);
	}
	
	private void shift()
	{
		if (!i_inter.isEmpty())
		{
			for (int i=i_inter.size()-1; i>=0; i--)
				i_stack.push(i_inter.get(i));
			
			i_inter.clear();
		}
		
		i_stack.push(i_input++);
	}
	
	private void reduce()
	{
		i_stack.pop();
	}
	
	private void pass()
	{
		i_inter.push(i_stack.pop());
	}
	
	private String getSnapshot()
	{
		StringBuilder build = new StringBuilder();
		int i;
		
		for (i=i_stack.size()-1; i>0; i--)
		{
			build.append(i_stack.get(i));
			build.append(StringConst.COMMA);
		}	build.append(StringConst.PIPE);
		
		for (i=i_inter.size()-1; i>=0; i--)
		{
			build.append(i_inter.get(i));
			build.append(StringConst.COMMA);
		}	build.append(StringConst.PIPE);
		
		build.append(i_input);
		return build.toString();
	}
	
//	====================================== FEATURES ======================================

	@Override
	public boolean extractWordFormFeature(DEPNode node)
	{
		return true;
	}
	
	public int distanceBetweenStackAndInput()
	{
		int sID = getStackID();
		if (sID == DEPLib.ROOT_ID) return -1;
		
		int d = i_input - sID; 
		return (d > 6) ? 6 : d;
	}
	
//	====================================== BRANCH ======================================

	public boolean startBranching()
	{
		if (beam_size <= 1 || l_branches.isEmpty()) return false;
		DSUtils.sortReverseOrder(l_branches);
		
		if (l_branches.size() > beam_size-1)
			l_branches = l_branches.subList(0, beam_size-1);
		
		beam_index  = 0;
		save_branch = false;
		return true;
	}
	
	public boolean nextBranch()
	{
		if (beam_index < l_branches.size())
		{
			l_branches.get(beam_index++).reset();
			return true;
		}
		
		return false;
	}
	
	public void saveBranch(StringPrediction[] ps, DEPLabel autoLabel)
	{
		if (save_branch)
		{
			StringPrediction fst = ps[0];
			StringPrediction snd = ps[1];
			
			if (fst.getScore() - snd.getScore() < 1)
				addBranch(autoLabel, new DEPLabel(snd));
		}
	}

	private void addBranch(DEPLabel fstLabel, DEPLabel sndLabel)
	{
		if (!fstLabel.isArc(sndLabel) || !fstLabel.isList(sndLabel))
			l_branches.add(new DEPBranch(sndLabel));
	}
	
	public void mergeBranches()
	{
		d_merge.merge();
	}
	
	private class DEPBranch implements Comparable<DEPBranch>
	{
		private DEPArc[]  heads;
		private IntPStack stack;
		private IntPStack inter;
		private int       input;
		private DEPLabel  label;
		private double    score;
		
		public DEPBranch(DEPLabel nextLabel)
		{
			heads = d_tree.getHeads(i_input+1);
			stack = new IntPStack(i_stack);
			inter = new IntPStack(i_inter);
			input = i_input;
			label = nextLabel;
			score = nextLabel.getScore();
		}
		
		public void reset()
		{
			d_tree.setHeads(heads);
			i_stack = stack;
			i_inter = inter;
			i_input = input;
			next(label);
		}

		@Override
		public int compareTo(DEPBranch o)
		{
			return MathUtils.signum(score - o.score);
		}
	}
	
//	====================================== STATE HISTORY ======================================
	
//	private StringBuilder s_states = new StringBuilder();
//	
//	private void saveState(DEPLabel label)
//	{
//		s_states.append(label.toString());
//		s_states.append(StringConst.TAB);
//		s_states.append(getState(d_stack));
//		s_states.append(StringConst.TAB);
//		s_states.append(getState(d_inter));
//		s_states.append(StringConst.TAB);
//		s_states.append(i_input);
//		s_states.append(StringConst.NEW_LINE);
//	}
//	
//	private String getState(List<DEPNode> nodes)
//	{
//		StringBuilder build = new StringBuilder();
//		build.append("[");
//		
//		if (nodes.size() > 0)
//			build.append(nodes.get(0).getID());
//		
//		if (nodes.size() > 1)
//		{
//			build.append(",");
//			build.append(nodes.get(nodes.size()-1).getID());
//		}
//		
//		build.append("]");
//		return build.toString();
//	}
//	
//	public String stateHistory()
//	{
//		return s_states.toString();
//	}
}