package br.ufc.storm.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXB;

import org.apache.axis2.jaxws.common.config.AddressingWSDLExtensionValidator;

import br.ufc.storm.jaxb.AbstractComponentType;
import br.ufc.storm.jaxb.AbstractUnitType;
import br.ufc.storm.jaxb.ContextContract;
import br.ufc.storm.jaxb.ContextParameterType;
import br.ufc.storm.jaxb.SliceType;
import br.ufc.storm.model.ResolutionNode;
import br.ufc.storm.exception.DBHandlerException;
import br.ufc.storm.exception.ResolveException;
import br.ufc.storm.exception.XMLException;

public class AbstractComponentHandler extends DBHandler{
	private final static String SELECT_ALL_ABSTRACTCOMPONENT = "select ac_name,ac_id,supertype_id from abstract_component;";
	private final static String INSERT_ABSTRACT_COMPONENT ="INSERT INTO abstract_component (ac_name, supertype_id) VALUES (?, ?) RETURNING ac_id;" ;
	private final static String SELECT_COMPONENT_ID = "select ac_id from abstract_component where ac_name=?;";
	private final static String SELECT_ABSTRACT_COMPONENT_BY_ID = "select * from abstract_component WHERE ac_id = ?;";
	private final static String SELECT_ABSTRACT_COMPONENT_BY_CC_ID = "select * from abstract_component A, context_contract B WHERE A.ac_id = B.ac_id AND cc_id = ?;";
	private final static String SELECT_COMPONENT_NAME = "select ac_name from abstract_component where ac_id=?;";
	private static final String SELECT_ALL_SLICES = "SELECT * FROM slice WHERE ac_id = ?;";
	private final static String UPDATE_ABSTRACT_COMPONENT = "update abstract_component set enabled=false where ac_name = ?;";

	/**
	 * This method gets the list of all abstract components in the library
	 * @return List of components
	 * @throws SQLException 
	 * @throws DBHandlerException
	 */

	public static List<AbstractComponentType> listAbstractComponents() throws DBHandlerException{
		try {
			Connection con = getConnection();
			int ac_id, supertype_id;
			String name;
			ArrayList<AbstractComponentType> list = new ArrayList<AbstractComponentType>();
			PreparedStatement prepared = con.prepareStatement(SELECT_ALL_ABSTRACTCOMPONENT);
			ResultSet resultSet = prepared.executeQuery();
			while (resultSet.next()) {
				name = resultSet.getString("ac_name");
				ac_id = resultSet.getInt("ac_id");
				supertype_id = resultSet.getInt("supertype_id"); 
				AbstractComponentType ac = new AbstractComponentType();
				ac.setIdAc(ac_id);
				ac.setName(name);
				ac.setSupertype(new AbstractComponentType());
				ac.getSupertype().setIdAc(supertype_id);
				try {
					ac.getSupertype().setName(AbstractComponentHandler.getAbstractComponentName(supertype_id));
				} catch (DBHandlerException e) {
					throw new DBHandlerException("Supertype not found", e);
				}
				list.add(ac);
			}
			return list;
		} catch (SQLException e1) {
			throw new DBHandlerException("A sql error occurred: ", e1);
		}

	}

	/**
	 * This method adds an Abstract Component into components library 
	 * @param ac object 
	 * @return Abstract Component added id 
	 * @throws ResolveException 
	 * @throws SQLException 
	 * @throws XMLException 
	 */
	
	//TODO: Concluir o cadastro de variáveis
	public static void addAbstractComponent(AbstractComponentType ac, Map<String, Integer> sharedVariables) throws DBHandlerException, ResolveException{

		try {

			Connection con = DBHandler.getConnection();
			con.setAutoCommit(false);
			PreparedStatement prepared = con.prepareStatement(INSERT_ABSTRACT_COMPONENT);
			prepared.setString(1, ac.getName()); 
			prepared.setInt(2, ac.getSupertype().getIdAc());
			ResultSet result = prepared.executeQuery();
			if(result.next()){
				ac.setIdAc(result.getInt("ac_id"));
			}else{
				throw new DBHandlerException("Abstract component id not returned");
			}
			if(ac.getContextParameter()!=null){
				if(sharedVariables == null){
					sharedVariables = new HashMap<String, Integer>();
				}
				for(ContextParameterType cp:ac.getContextParameter()){
					//TODO: Corrigir a passagem de variáveis
					//					Se tem variavel compartilhada, adicionar no hashmap

					String boundName = null;
					if(cp.getBound() != null){
						boundName = cp.getBound().getCcName();
					}else{
						//						Parameter without bound, must throw an exception
						throw new DBHandlerException("Parameter without bound");
					}

					if(cp.getContextVariableProvided()!=null){
						sharedVariables.put(cp.getContextVariableProvided(), cp.getCpId());
					}
					cp.setCpId(ContextParameterHandler.addContextParameter(cp.getName(),boundName, ac.getName(), null, cp.getBoundValue(), cp.getContextVariableRequired(), sharedVariables));
				}
			}
			//Add each abstract unit
			for(AbstractUnitType aut: ac.getAbstractUnit()){
				aut.setAuId(AbstractUnitHandler.addAbstractUnit(aut.getAcId(), aut.getAuName()));;
			}
			for(AbstractComponentType inner:ac.getInnerComponents()){
				addAbstractComponent(inner, sharedVariables);
			}
			for(SliceType st:ac.getSlices()){
				SliceHandler.addSlice(st, ac.getIdAc());
			}
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: "+e.getMessage());
		}

	}

	/**
	 * This method get an abstract component from components library
	 * @param compName
	 * @return Abstract component if found, else it returns null
	 * @throws DBHandlerException 
	 */
	public static AbstractComponentType getAbstractComponent(int ac_id) throws DBHandlerException{
		try {
			ResolutionNode.setup();
		} catch (ResolveException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		AbstractComponentType ac;
		ac = getAbstractComponentPartial(ac_id);
		ac.getInnerComponents().addAll(ContextContractHandler.getInnerComponents(ac_id));
		ac.getContextParameter().addAll(ResolutionNode.resolutionTree.findNode(ac_id).getCps());
		ac.getQualityParameters().addAll(CalculatedArgumentHandler.getCalculatedParameters(ac_id, ContextParameterHandler.QUALITY));
		ac.getCostParameters().addAll(CalculatedArgumentHandler.getCalculatedParameters(ac_id, ContextParameterHandler.COST));
		ac.getRankingParameters().addAll(CalculatedArgumentHandler.getCalculatedParameters(ac_id, ContextParameterHandler.RANKING));
		ac.getAbstractUnit().addAll(AbstractUnitHandler.getAbstractUnits(ac_id));
		ac.getSlices().addAll(getSlices(ac_id));
		return ac;
	}

	/**
	 * This method get an abstract component from components library
	 * @param compName
	 * @return Abstract component if found, else it returns null
	 * @throws SQLException 
	 * @throws DBHandlerException 
	 */
	//TODO: Refactor this method
	public static AbstractComponentType getAbstractComponentPartial(int ac_id) throws DBHandlerException{
		try {
			Connection con = getConnection();
			Integer supertype_id = null;
			String name = null;
			PreparedStatement prepared = con.prepareStatement(SELECT_ABSTRACT_COMPONENT_BY_ID); 
			prepared.setInt(1, ac_id);
			ResultSet resultSet = prepared.executeQuery(); 
			if (resultSet.next()) { 
				if(resultSet.getBoolean("enabled") == false){
					closeConnnection(con);
					return null;
				}
				name=resultSet.getString("ac_name");
				supertype_id = resultSet.getInt("supertype_id"); 
				AbstractComponentType ac = new AbstractComponentType();
				ac.setIdAc(ac_id);
				ac.setName(name);
				if(supertype_id!=null){
					ac.setSupertype(new AbstractComponentType());
					ac.getSupertype().setIdAc(supertype_id);
					ac.getSupertype().setName(getAbstractComponentName(supertype_id));
				}
				//			ac.setParent(new AbstractComponentType());
				//			ac.getParent().setIdAc(parent);
				//			List<ContextParameterType> t = DBHandler.getAllContextParameterFromAbstractComponent(ac_id);
				//			ac.getContextParameter().addAll(t);
				return ac; 
			}else{
				throw new DBHandlerException("Abstract component not exists");
			}
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		} 

	}

	/**
	 * This method gets an abstract component from a context contract id
	 * @param cc_id
	 * @return
	 * @throws SQLException 
	 * @throws DBHandlerException 
	 */

	public static AbstractComponentType getAbstractComponentFromContextContractID(int cc_id) throws DBHandlerException{
		try {
			Connection con = getConnection();
			PreparedStatement prepared = con.prepareStatement(SELECT_ABSTRACT_COMPONENT_BY_CC_ID); 
			prepared.setInt(1, cc_id);
			ResultSet resultSet = prepared.executeQuery(); 
			if(resultSet.next()) { 
				if(resultSet.getBoolean("enabled") == false){
					throw new DBHandlerException("Abstract component disabled, can't be caught");
				}else{
					return getAbstractComponent(resultSet.getInt("ac_id"));	
				}
			}else{
				throw new DBHandlerException("Abstract component not found");
			}
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		}

	}

	/**
	 * This method returns an abstract component name given an id
	 * @param id
	 * @return abstract component name
	 * @throws SQLException 
	 * @throws DBHandlerException 
	 */
	public static String getAbstractComponentName(int id) throws DBHandlerException{
		try {
			Connection con = getConnection();
			PreparedStatement prepared;
			prepared = con.prepareStatement(SELECT_COMPONENT_NAME);
			prepared.setInt(1, id); 
			ResultSet resultSet = prepared.executeQuery(); 
			if(resultSet.next()) { 
				return resultSet.getString("ac_name");
			}else{
				throw new DBHandlerException("Abstract component not found");
			}
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		} 


	}

	/**
	 * This method returns an abstract component id given an name
	 * @param name
	 * @return abstract component id
	 * @throws SQLException 
	 * @throws DBHandlerException 
	 */
	public static Integer getAbstractComponentID(String name) throws DBHandlerException{

		try {
			Connection con = getConnection(); 
			int ac_id = 0;
			PreparedStatement prepared;
			prepared = con.prepareStatement(SELECT_COMPONENT_ID);
			prepared.setString(1, name); 
			ResultSet resultSet = prepared.executeQuery(); 
			if(resultSet.next()) { 
				ac_id = resultSet.getInt("ac_id"); 
				return ac_id;
			}else{
				return null;
			}
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		} 
	}

	/**
	 * This method caches all abstract slices from a component 
	 * @param ac_id
	 * @return List of abstract slices
	 * @throws DBHandlerException
	 */

	private static List<SliceType> getSlices(int ac_id) throws DBHandlerException {

		try {
			Connection con = getConnection();
			ArrayList<SliceType> slices = new ArrayList<SliceType>();
			PreparedStatement prepared = con.prepareStatement(SELECT_ALL_SLICES);
			prepared.setInt(1, ac_id);
			ResultSet resultSet = prepared.executeQuery();
			while (resultSet.next()) {				
				SliceType slc = new SliceType();
				slc.setSliceId(resultSet.getInt("slice_id"));
				slc.setSliceId(resultSet.getInt("inner_component_id"));
				slc.setSliceId(resultSet.getInt("inner_unit_id"));
				slices.add(slc);
			} 
			return slices; 
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		}

	}

	/**
	 * This method is responsible for set a component as removed, disabling it in the database
	 * @param name Abstract component name
	 * @return true if correctly set as removed
	 * @throws SQLException 
	 */
	public static void setObsolete(String name) throws DBHandlerException{

		try {
			Connection con = getConnection(); 
			PreparedStatement prepared = con.prepareStatement(UPDATE_ABSTRACT_COMPONENT);
			prepared.setString(1, name); 
			prepared.execute();
		} catch (SQLException e) {
			throw new DBHandlerException("A sql error occurred: ", e);
		} 

	}



}
