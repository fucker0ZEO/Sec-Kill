package com.secKillingProject.dao;

import com.secKillingProject.dataObject.PromoDO;

/**
 * @author fucker
 */
public interface PromoDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    int insert(PromoDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    int insertSelective(PromoDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    PromoDO selectByPrimaryKey(Integer id);

    /**根据itemId查询出该行活动记录*/
    PromoDO selectByItemId(Integer itemId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    int updateByPrimaryKeySelective(PromoDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table promo
     *
     * @mbg.generated Fri Apr 02 21:06:49 CST 2021
     */
    int updateByPrimaryKey(PromoDO record);
}