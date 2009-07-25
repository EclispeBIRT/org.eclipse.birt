/*******************************************************************************
 * Copyright (c) 2004, 2005, 2006, 2007, 2008, 2009 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.adapter.oda.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.birt.report.model.api.DynamicFilterParameterHandle;
import org.eclipse.birt.report.model.api.FilterConditionHandle;
import org.eclipse.birt.report.model.api.OdaDataSetHandle;
import org.eclipse.birt.report.model.api.ParameterHandle;
import org.eclipse.birt.report.model.api.PropertyHandle;
import org.eclipse.birt.report.model.api.SortHintHandle;
import org.eclipse.birt.report.model.api.StructureFactory;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.eclipse.birt.report.model.api.elements.structures.FilterCondition;
import org.eclipse.birt.report.model.api.elements.structures.SortHint;
import org.eclipse.birt.report.model.api.util.StringUtil;
import org.eclipse.birt.report.model.elements.interfaces.IDataSetModel;
import org.eclipse.datatools.connectivity.oda.design.CompositeFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.CustomFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.DataSetDesign;
import org.eclipse.datatools.connectivity.oda.design.DesignFactory;
import org.eclipse.datatools.connectivity.oda.design.DynamicFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.ExpressionArguments;
import org.eclipse.datatools.connectivity.oda.design.ExpressionParameterDefinition;
import org.eclipse.datatools.connectivity.oda.design.ExpressionVariable;
import org.eclipse.datatools.connectivity.oda.design.FilterExpression;
import org.eclipse.datatools.connectivity.oda.design.NullOrderingType;
import org.eclipse.datatools.connectivity.oda.design.ParameterDefinition;
import org.eclipse.datatools.connectivity.oda.design.ResultSetCriteria;
import org.eclipse.datatools.connectivity.oda.design.ResultSetDefinition;
import org.eclipse.datatools.connectivity.oda.design.SortDirectionType;
import org.eclipse.datatools.connectivity.oda.design.SortKey;
import org.eclipse.datatools.connectivity.oda.design.SortSpecification;
import org.eclipse.emf.common.util.EList;

/**
 * The utility class that converts between ROM filter conditions and ODA filter
 * expression
 * 
 * @see FilterConditionHandle
 * @see FilterExpression
 */
public class ResultSetCriteriaAdapter
{

	/**
	 * The data set handle.
	 */

	private final OdaDataSetHandle setHandle;

	/**
	 * The data set design.
	 */

	private final DataSetDesign setDesign;

	/**
	 * Parameter convert utility
	 */
	private final AbstractReportParameterAdapter paramAdapter = new AbstractReportParameterAdapter( );

	/**
	 * Constant to seperate date set name and column name.
	 */
	private final String SEPERATOR = ":";

	/**
	 * Prefix constant for custom expression.
	 */
	private final String CUSTOM_PREFIX = "#";

	/**
	 * Prefix constant for dynamic expression.
	 */
	private final String DYNAMIC_PREFIX = "!";

	/**
	 * The constructor.
	 * 
	 * @param setHandle
	 *            the data set handle
	 * @param setDesign
	 *            the data set design
	 * 
	 */
	public ResultSetCriteriaAdapter( OdaDataSetHandle setHandle,
			DataSetDesign setDesign )
	{
		this.setHandle = setHandle;
		this.setDesign = setDesign;
	}

	/**
	 * Updates rom filter and sort hints.
	 * 
	 * @throws SemanticException
	 */
	public void updateROMSortAndFilter( ) throws SemanticException
	{
		ResultSetDefinition resultSet = setDesign.getPrimaryResultSet( );

		if ( resultSet == null )
			return;

		ResultSetCriteria criteria = resultSet.getCriteria( );

		if ( criteria == null )
			return;

		updateROMSortHint( criteria );

		updateROMFilterCondition( criteria );

	}

	/**
	 * Updates oda result set criteria.
	 * 
	 */
	public void updateODAResultSetCriteria( )
	{
		ResultSetDefinition resultSet = setDesign.getPrimaryResultSet( );
		if ( resultSet == null )
			return;

		// if criteria is null, a new criteria will be created.
		ResultSetCriteria criteria = resultSet.getCriteria( );
		if ( criteria == null )
		{
			criteria = DesignFactory.eINSTANCE.createResultSetCriteria( );
			resultSet.setCriteria( criteria );
		}

		updateODASortKey( criteria );
		updateOdaFilterExpression( criteria );
	}

	/**
	 * Updates oda filter expression by ROM filter condition.
	 * 
	 * @param criteria
	 *            the result set criteria.
	 */
	private void updateOdaFilterExpression( ResultSetCriteria criteria )
	{

		int count = 0;
		FilterExpression filterExpr = null;
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterHandle = (FilterConditionHandle) iter
					.next( );
			FilterExpression filter = createOdaFilterExpression( filterHandle );
			if ( filter == null )
			{
				continue;
			}
			count++;
			switch ( count )
			{
				case 1 :
					filterExpr = filter;
					break;
				case 2 :
					CompositeFilterExpression compositeFilterExp = DesignFactory.eINSTANCE
							.createCompositeFilterExpression( );
					compositeFilterExp.add( filterExpr );
					filterExpr = compositeFilterExp;
				default :
					( (CompositeFilterExpression) filterExpr ).add( filter );
			}
		}
		criteria.setFilterSpecification( filterExpr );
	}

	/**
	 * Updates oda sort key.
	 * 
	 */
	private void updateODASortKey( ResultSetCriteria criteria )

	{

		SortSpecification sortSpec = criteria.getRowOrdering( );

		// if an Oda data set has no BIRT sort hints, the Adapter should create
		// an empty SortSpecification.
		if ( sortSpec == null )
		{
			sortSpec = DesignFactory.eINSTANCE.createSortSpecification( );
			criteria.setRowOrdering( sortSpec );
		}

		EList<SortKey> list = sortSpec.getSortKeys( );

		// clear the original value.
		list.clear( );

		Iterator<SortHintHandle> iter = setHandle.sortHintsIterator( );

		while ( iter.hasNext( ) )
		{
			SortHintHandle handle = iter.next( );
			SortKey key = DesignFactory.eINSTANCE.createSortKey( );
			key.setColumnName( handle.getColumnName( ) );
			key.setColumnPosition( handle.getPosition( ) );

			String ordering = handle.getNullValueOrdering( );

			setODANullValueOrdering( key, ordering );

			key.setOptional( handle.isOptional( ) );

			// default value
			if ( DesignChoiceConstants.SORT_DIRECTION_ASC.equals( handle
					.getDirection( ) ) )
			{
				key.setSortDirection( SortDirectionType.ASCENDING );
			}
			else if ( DesignChoiceConstants.SORT_DIRECTION_DESC.equals( handle
					.getDirection( ) ) )
			{
				key.setSortDirection( SortDirectionType.DESCENDING );
			}

			list.add( key );
		}

	}

	/**
	 * Updates null value ordering value in oda.
	 * 
	 * @param key
	 *            the sort key.
	 * @param ordering
	 *            the ordering.
	 */
	private void setODANullValueOrdering( SortKey key, String ordering )
	{
		if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISFIRST
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.NULLS_FIRST );
		}
		else if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISLAST
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.NULLS_LAST );
		}
		else if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_UNKNOWN
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.UNKNOWN );
		}
	}

	/**
	 * Updates rom sort hint.
	 * 
	 * @throws SemanticException
	 */
	private void updateROMSortHint( ResultSetCriteria criteria )
			throws SemanticException
	{

		SortSpecification sortSpec = criteria.getRowOrdering( );

		if ( sortSpec == null )
			return;

		PropertyHandle propHandle = setHandle
				.getPropertyHandle( IDataSetModel.SORT_HINTS_PROP );

		// clear the original value.

		propHandle.clearValue( );

		EList<SortKey> list = sortSpec.getSortKeys( );

		for ( int i = 0; i < list.size( ); i++ )
		{
			SortKey key = list.get( i );
			SortHint sortHint = StructureFactory.createSortHint( );

			sortHint.setProperty( SortHint.COLUMN_NAME_MEMBER, key
					.getColumnName( ) );
			sortHint.setProperty( SortHint.POSITION_MEMBER, key
					.getColumnPosition( ) );
			sortHint
					.setProperty( SortHint.IS_OPTIONAL_MEMBER, key.isOptional( ) );

			SortDirectionType sortType = key.getSortDirection( );

			if ( SortDirectionType.ASCENDING.equals( sortType ) )
			{
				sortHint.setProperty( SortHint.DIRECTION_MEMBER,
						DesignChoiceConstants.SORT_DIRECTION_ASC );
			}
			else if ( SortDirectionType.DESCENDING.equals( sortType ) )
			{
				sortHint.setProperty( SortHint.DIRECTION_MEMBER,
						DesignChoiceConstants.SORT_DIRECTION_DESC );
			}

			NullOrderingType type = key.getNullValueOrdering( );

			setROMNullValueOrdering( sortHint, type );

			propHandle.addItem( sortHint );

		}
	}

	/**
	 * Updates null value ordering value in rom.
	 * 
	 * @param hint
	 *            sort hint.
	 * @param type
	 *            the null ordering type.
	 */
	private void setROMNullValueOrdering( SortHint hint, NullOrderingType type )
	{
		if ( NullOrderingType.NULLS_FIRST.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISFIRST );
		}
		else if ( NullOrderingType.NULLS_LAST.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISLAST );
		}
		else if ( NullOrderingType.UNKNOWN.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_UNKNOWN );
		}

	}

	/**
	 * Updates rom filter condition by ODA filter expression.
	 * 
	 * @param criteria
	 *            result set criteria.
	 * @throws SemanticException
	 */
	private void updateROMFilterCondition( ResultSetCriteria criteria )
			throws SemanticException
	{
		FilterExpression filterExpression = null;

		filterExpression = criteria.getFilterSpecification( );

		if ( filterExpression == null )
		{
			return;
		}
		Map<String, FilterExpression> filterExprMap = buildFilterExpressionMap( filterExpression );

		// clears up old filter conditions and finds parameters to refresh
		cleanUpROMFilterCondition( filterExprMap );

		// update exists filter conditions
		updateExistingROMFilterConditions( filterExprMap );
		// sets new filter conditions
		createROMFilterConditions( filterExprMap );

		filterExprMap.clear( );
		filterExprMap = null;
	}

	/**
	 * Builds the filter expression map to convert
	 * 
	 * @param filterExpr
	 *            the filter expression
	 * @return the map containing filter expression to convert
	 */
	private Map<String, FilterExpression> buildFilterExpressionMap(
			FilterExpression filterExpr )
	{
		HashMap<String, FilterExpression> filterExpressions = new LinkedHashMap<String, FilterExpression>( );
		if ( filterExpr != null )
		{
			if ( filterExpr instanceof CompositeFilterExpression )
			{
				CompositeFilterExpression compositeFilterExp = (CompositeFilterExpression) filterExpr;
				for ( FilterExpression child : compositeFilterExp.getChildren( ) )
				{
					filterExpressions
							.putAll( buildFilterExpressionMap( child ) );
				}
			}
			else
			{
				String key = getMapKey( filterExpr );
				if ( key != null )
				{
					if ( filterExpr instanceof CustomFilterExpression )
					{
						filterExpressions.put( key, filterExpr );
					}
					else if ( filterExpr instanceof DynamicFilterExpression )
					{
						DynamicFilterExpression dynamicFilterExp = (DynamicFilterExpression) filterExpr;
						ExpressionArguments arguments = dynamicFilterExp
								.getContextArguments( );
						if ( arguments != null
								&& arguments
										.getExpressionParameterDefinitions( ) != null
								&& !arguments
										.getExpressionParameterDefinitions( )
										.isEmpty( ) )
						{
							filterExpressions.put( key, dynamicFilterExp );
						}
					}
				}
			}
		}
		return filterExpressions;
	}

	/**
	 * Returns the map key for the given dynamic filter expression
	 * 
	 * @param filterExpr
	 *            the filter expression
	 * @return the key for the given filter expression
	 */
	private String getMapKey( FilterExpression filterExpr )
	{
		String key = null;
		String columnExpr = getColumnExpr( filterExpr );
		if ( !StringUtil.isBlank( columnExpr ) )
		{
			if ( filterExpr instanceof CustomFilterExpression )
			{
				key = CUSTOM_PREFIX + filterExpr.toString( );
			}
			else if ( filterExpr instanceof DynamicFilterExpression )
			{
				key = DYNAMIC_PREFIX + setHandle.getName( ) + SEPERATOR
						+ columnExpr;
			}
			else
			{
				assert false;
			}
		}
		return key;
	}

	/**
	 * Returns the column expression defined in the given filter expression for
	 * filter condition.
	 * 
	 * @param filterExpr
	 *            the filter expression
	 * @return the column expression defined in the given filter expression
	 */
	private String getColumnExpr( FilterExpression filterExpr )
	{
		ExpressionVariable variable = null;
		if ( filterExpr instanceof CustomFilterExpression )
		{
			variable = ( (CustomFilterExpression) filterExpr )
					.getContextVariable( );
		}
		else if ( filterExpr instanceof DynamicFilterExpression )
		{
			variable = ( (DynamicFilterExpression) filterExpr )
					.getContextVariable( );
		}
		if ( variable != null )
		{
			return variable.getIdentifier( );
		}
		return null;
	}

	/**
	 * Returns the map key for the given filter condition
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition
	 * @return the key for the given filter handle,or null if the filter
	 *         condition is not dynamic.
	 */
	private String getMapKey( FilterConditionHandle filterConditionHandle )
	{
		String key = null;
		if ( !StringUtil.isBlank( filterConditionHandle
				.getDynamicFilterParameter( ) ) )
		{
			ParameterHandle parameterHandle = setHandle.getModuleHandle( )
					.findParameter(
							filterConditionHandle.getDynamicFilterParameter( ) );
			if ( parameterHandle instanceof DynamicFilterParameterHandle )
			{
				DynamicFilterParameterHandle dynamicFilterParamHandle = (DynamicFilterParameterHandle) parameterHandle;
				key = DYNAMIC_PREFIX
						+ dynamicFilterParamHandle.getDataSetName( )
						+ SEPERATOR + dynamicFilterParamHandle.getColumn( );
			}
		}
		return key;
	}

	/**
	 * Updates existing filter conditions with the given filter expressions
	 * 
	 * @param filterExpMap
	 *            the map containing the given filter expressions
	 * @throws SemanticException
	 */
	private void updateExistingROMFilterConditions(
			Map<String, FilterExpression> filterExpMap )
			throws SemanticException
	{
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterConditionHandle = (FilterConditionHandle) iter
					.next( );
			String key = getMapKey( filterConditionHandle );
			if ( key != null && filterExpMap.containsKey( key ) )
			{
				DynamicFilterParameterHandle dynamicFilterParamHandle = (DynamicFilterParameterHandle) setHandle
						.getModuleHandle( ).findParameter(
								filterConditionHandle
										.getDynamicFilterParameter( ) );
				updateDynamicFilterCondition( filterConditionHandle,
						(DynamicFilterExpression) filterExpMap.get( key ),
						dynamicFilterParamHandle );
				// Removes the filter from the map after updated
				filterExpMap.remove( key );
			}
			else
			{// not expected
				assert false;
			}
		}
	}

	/**
	 * Creates new filter conditions by the given filter expressions
	 * 
	 * @param filterExpMap
	 *            the map containing filter expressions
	 * @throws SemanticException
	 */
	private void createROMFilterConditions(
			Map<String, FilterExpression> filterExpMap )
			throws SemanticException
	{
		for ( FilterExpression filterExpr : filterExpMap.values( ) )
		{
			FilterCondition filterCondition = StructureFactory
					.createFilterCond( );
			filterCondition.setExpr( getColumnExpr( filterExpr ) );
			FilterConditionHandle filterConditionHandle = (FilterConditionHandle) setHandle
					.getPropertyHandle( IDataSetModel.FILTER_PROP ).addItem(
							filterCondition );
			if ( filterExpr instanceof CustomFilterExpression )
			{
				CustomFilterExpression customFilterExp = (CustomFilterExpression) filterExpr;
				updateCustomFilterCondition( filterConditionHandle,
						customFilterExp );
			}
			else if ( filterExpr instanceof DynamicFilterExpression )
			{
				DynamicFilterExpression dynamicFilterExp = (DynamicFilterExpression) filterExpr;
				// creates new dynamic filter parameter
				DynamicFilterParameterHandle dynamicFilterParamHandle = setHandle
						.getModuleHandle( ).getElementFactory( )
						.newDynamicFilterParameter( null );
				dynamicFilterParamHandle.setDataSetName( setHandle.getName( ) );
				dynamicFilterParamHandle.setColumn( dynamicFilterExp
						.getContextVariable( ).getIdentifier( ) );
				setHandle.getModuleHandle( ).getParameters( ).add(
						dynamicFilterParamHandle );
				// sets the reference
				filterConditionHandle
						.setDynamicFilterParameter( dynamicFilterParamHandle
								.getName( ) );
				// updates the dynamic filter parameter
				updateDynamicFilterCondition( filterConditionHandle,
						dynamicFilterExp, dynamicFilterParamHandle );
			}
		}
	}

	/**
	 * Updates the filter condition by the given custom filter expression
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition to update
	 * @param customFilterExpr
	 *            the custom filter expression
	 * @throws SemanticException
	 */
	private void updateCustomFilterCondition(
			FilterConditionHandle filterConditionHandle,
			CustomFilterExpression customFilterExpr ) throws SemanticException
	{
		filterConditionHandle.setExtensionName( customFilterExpr
				.getDeclaringExtensionId( ) );
		filterConditionHandle.setExtensionExprId( customFilterExpr.getId( ) );
		filterConditionHandle.setPushDown( true );
		filterConditionHandle.setOptional( customFilterExpr.isOptional( ) );
	}

	/**
	 * Updates the filter condition by the given dynamic filter expression
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition to update
	 * @param dynamicFilterExpr
	 *            the dynamic filter expression
	 * @throws SemanticException
	 */
	private void updateDynamicFilterCondition(
			FilterConditionHandle filterConditionHandle,
			DynamicFilterExpression dynamicFilterExpr,
			DynamicFilterParameterHandle dynamicFilterParamHandle )
			throws SemanticException
	{
		ExpressionVariable variable = dynamicFilterExpr.getContextVariable( );
		ExpressionArguments arguments = dynamicFilterExpr.getContextArguments( );
		// Only convert the first parameter
		ExpressionParameterDefinition paramDefn = arguments
				.getExpressionParameterDefinitions( ).get( 0 );
		if ( paramDefn != null )
		{
			filterConditionHandle.setOptional( dynamicFilterExpr.isOptional( ) );
			filterConditionHandle.setExpr( variable.getIdentifier( ) );
			updateDynamicFilterParameter( dynamicFilterParamHandle, paramDefn );
		}
	}

	/**
	 * Updates the dynamic filter parameter by the given expression parameter.
	 * 
	 * @param dynamicFilterParamHandle
	 *            the handle of the dynamic filter parameter to update
	 * @param expParamDefn
	 *            the definition of the given expression parameter
	 * @throws SemanticException
	 */

	private void updateDynamicFilterParameter(
			DynamicFilterParameterHandle dynamicFilterParamHandle,
			ExpressionParameterDefinition expParamDefn )
			throws SemanticException
	{
		ParameterDefinition paramDefn = expParamDefn.getDynamicInputParameter( );
		if ( paramDefn == null )
		{
			return;
		}
		paramAdapter.updateAbstractScalarParameter( dynamicFilterParamHandle,
				paramDefn, null, setHandle );
	}

	/**
	 * Clears up all unnecessary dynamic filter parameter and filter conditions
	 * 
	 * @param filterExpMap
	 *            the map contains filter expressions
	 * @throws SemanticException
	 * 
	 */
	private void cleanUpROMFilterCondition(
			Map<String, FilterExpression> filterExpMap )
			throws SemanticException
	{
		ArrayList<FilterCondition> dropList = new ArrayList<FilterCondition>( );
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterHandle = (FilterConditionHandle) iter
					.next( );
			String dynamicParameterName = filterHandle
					.getDynamicFilterParameter( );
			String key = getMapKey( filterHandle );
			// Check if contains such filter.
			if ( key != null && !filterExpMap.containsKey( key ) )
			{
				// Remove the filter condition which is not contained.
				if ( !StringUtil.isBlank( dynamicParameterName ) )
				{
					ParameterHandle parameterHandle = setHandle
							.getModuleHandle( ).findParameter(
									dynamicParameterName );
					parameterHandle.drop( );
				}
				dropList.add( (FilterCondition) filterHandle.getStructure( ) );
			}
		}
		for ( FilterCondition fc : dropList )
		{
			setHandle.removeFilter( fc );
		}

	}

	/**
	 * Creates the oda filter expression by the given filter condition.
	 * 
	 * @param filterHandle
	 *            the handle of the given filter condition
	 * @return the filter expression created
	 */
	private FilterExpression createOdaFilterExpression(
			FilterConditionHandle filterHandle )
	{
		FilterExpression filterExpr = null;
		ExpressionVariable variable = DesignFactory.eINSTANCE
				.createExpressionVariable( );
		variable.setIdentifier( filterHandle.getExpr( ) );
		if ( !StringUtil.isBlank( filterHandle.getExtensionName( ) ) )
		{
			CustomFilterExpression customFilterExpr = DesignFactory.eINSTANCE
					.createCustomFilterExpression( );
			customFilterExpr.setContextVariable( variable );
			customFilterExpr.setDeclaringExtensionId( filterHandle
					.getExtensionName( ) );
			customFilterExpr.setId( filterHandle.getExtensionExprId( ) );
			customFilterExpr.setIsOptional( filterHandle.isOptional( ) );
			filterExpr = customFilterExpr;
		}
		else if ( !StringUtil
				.isBlank( filterHandle.getDynamicFilterParameter( ) ) )
		{
			ParameterHandle paramHandle = setHandle.getModuleHandle( )
					.findParameter( filterHandle.getDynamicFilterParameter( ) );
			if ( paramHandle instanceof DynamicFilterParameterHandle )
			{
				DynamicFilterParameterHandle dynamicParamHandle = (DynamicFilterParameterHandle) paramHandle;
				DynamicFilterExpression dynamicFilterExpr = DesignFactory.eINSTANCE
						.createDynamicFilterExpression( );
				dynamicFilterExpr.setIsOptional( filterHandle.isOptional( ) );
				dynamicFilterExpr.setContextVariable( variable );

				ExpressionArguments arguments = DesignFactory.eINSTANCE
						.createExpressionArguments( );
				ParameterDefinition paramDefn = DesignFactory.eINSTANCE
						.createParameterDefinition( );

				paramAdapter.updateParameterDefinitionFromReportParam(
						paramDefn, dynamicParamHandle, setDesign );

				arguments.addDynamicParameter( paramDefn );
				dynamicFilterExpr.setContextArguments( arguments );

				filterExpr = dynamicFilterExpr;
			}
		}
		return filterExpr;
	}

}