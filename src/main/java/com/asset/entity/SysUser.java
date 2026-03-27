package com.asset.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private String userId;
    
    @TableField("ADAccount")
    private String adAccount;
    
    private String username;
    
    private String password;
    
    private String pinyin;
    
    private String gender;
    
    @TableField("empNo4")
    private String empNo4;
    
    @TableField("empNo7")
    private String empNo7;
    
    private String department;
    
    private Integer depId;
    
    private Integer teamId;
    
    private Integer groupId;
    
    private String position;
    
    private String base;
    
    private String cellphone1;
    
    private String cellphone2;
    
    private String ipPhone;
    
    private String fax;
    
    private String location1;
    
    private String phone1;
    
    private String station1;
    
    private String location2;
    
    private String phone2;
    
    private String station2;
    
    private String superior;
    
    private String createOperator;
    
    private LocalDateTime createTime;
    
    private String lastOperator;
    
    private LocalDateTime lastTime;
    
    @TableField("`rank`")
    private String rank;
}
