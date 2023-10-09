package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品和口味
     * @param dishDTO
     */
    @Transactional
    public void addWithFlavors(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);  //属性拷贝

        //向菜品表插入数据
        dishMapper.insert(dish);

        Long dishId = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!=null&&flavors.size()>0){
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入多条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO>page=dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 菜品批量删除
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //判断菜品能否删除
        //1.菜品是否起售
        for (Long id : ids) {
            Dish dish=dishMapper.getById(id);
            if(dish.getStatus()== StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //2.菜品是否有关联的套餐
        List<Long> setmealIds =setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds!=null && setmealIds.size()>0){
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //批量删除菜品
        dishMapper.deleteBatch(ids);
        //批量删除菜品关联的口味
        dishFlavorMapper.deleteBatch(ids);
    }

    /**
     * 根据id查询菜品和口味
     * @param id
     * @return
     */
    public DishVO getWihFlavor(Long id) {
        //查询菜品
        Dish dish=dishMapper.getById(id);
        //查询口味
        List<DishFlavor>dishFlavors=dishFlavorMapper.getByDishId(id);

        DishVO dishVO=new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        //修改菜品
        Dish dish=new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.update(dish);

        //删除菜品关联的口味
        Long dishId = dishDTO.getId();
        dishFlavorMapper.deleteByDishId(dishId);

        //新增菜品口味
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!=null&&flavors.size()>0){
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入多条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 起售停售菜品
     * @param status
     * @param id
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        //起售停售菜品
        Dish dish=Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

        //若停售菜品，则包含该菜品的套餐也将停售
        if(status==StatusConstant.DISABLE){
            //获取需要停售的套餐id
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);

            if(setmealIds!=null&&setmealIds.size()>0){
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal=Setmeal.builder()
                            .status(status)
                            .id(setmealId)
                            .build();
                    setmealMapper.update(setmeal);
                }
            }
        }
    }
}
