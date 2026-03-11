import random

def generate_sql():
    sql = []
    sql.append("SET NAMES utf8mb4;")
    sql.append("SET FOREIGN_KEY_CHECKS = 0;")
    sql.append("")
    sql.append("-- Clear existing data")
    sql.append("TRUNCATE TABLE `user`;")
    sql.append("TRUNCATE TABLE `product`;")
    sql.append("TRUNCATE TABLE `asset_file`;")
    sql.append("TRUNCATE TABLE `user_favorite_product`;")
    sql.append("TRUNCATE TABLE `edit_lock`;")
    sql.append("")
    
    # Users
    sql.append("-- Users")
    password_hash = "e10adc3949ba59abbe56e057f20f883e" 
    sql.append(f"INSERT INTO `user` (`id`, `username`, `password_hash`, `real_name`, `emp_no`, `role_type`) VALUES")
    sql.append(f"(1, 'admin', '{password_hash}', '系统管理员', 'ADMIN001', 1),")
    sql.append(f"(2, 'chendong', '{password_hash}', '陈东', 'NO.9527', 2),")
    sql.append(f"(3, 'linlin', '{password_hash}', '林琳', 'NO.9528', 2),")
    sql.append(f"(4, 'wangqiang', '{password_hash}', '王强', 'NO.9529', 3);")
    sql.append("")

    # Products
    sql.append("-- Products")
    teams = ["测试一团队", "测试二团队", "测试三团队", "测试四团队", "性能及非功能测试团队"]
    domains = ["金融核心域", "供应链域", "数据智能域", "基础架构域", "客户服务域"]
    banking_systems = [
        "核心银行系统", "个人网银系统", "企业网银系统", "手机银行APP", "贷款管理系统",
        "信用卡业务系统", "支付结算系统", "风险预警系统", "反洗钱监测系统", "客户关系管理系统(CRM)",
        "资产负债管理系统", "票据业务系统", "外汇交易系统", "期货交易系统", "理财销售系统",
        "柜面操作系统", "征信管理系统", "中间业务平台", "数据仓库(EDW)", "报表统计系统",
        "银企直连系统", "ATM终端管理系统", "POS收单系统", "电子档案管理系统", "人力资源管理系统",
        "财务核算系统", "办公自动化系统(OA)", "审计管理系统", "知识库管理系统", "呼叫中心系统",
        "移动展业系统", "供应链金融平台", "普惠金融系统", "同业拆借系统", "债券交易系统",
        "基金代销系统", "黄金交易系统", "保管箱管理系统", "押品管理系统", "额度管理系统",
        "绩效考核系统", "培训管理系统", "党建管理系统", "档案影像系统", "印章管理系统",
        "安全认证系统", "统一门户系统", "单点登录系统", "消息推送平台", "日志审计系统",
        "漏洞扫描系统", "堡垒机系统", "防火墙管理系统", "入侵检测系统", "态势感知平台",
        "云管理平台", "容器云平台", "微服务治理平台", "分布式数据库集群", "分布式缓存系统",
        "分布式消息队列", "分布式事务框架", "分布式配置中心", "分布式调度系统", "分布式文件系统",
        "大数据计算平台", "流式计算引擎", "机器学习平台", "图数据库系统", "全文检索系统",
        "区块链存证平台", "生物识别认证系统", "智能客服机器人", "智能投顾系统", "智能风控引擎",
        "智能营销平台", "智能审计系统", "智能运维平台", "智能监控系统", "智能报表系统"
    ]
    
    product_values = []
    for i in range(1, 81):
        team = teams[i % 5]
        domain = domains[i % 5]
        owner_id = 2 # Chendong
        asset_count = random.randint(5, 50)
        product_name = banking_systems[i-1]
        product_values.append(f"({i}, '{product_name}', '{team}', '{domain}', {owner_id}, {asset_count})")
    
    sql.append(f"INSERT INTO `product` (`id`, `product_name`, `team_name`, `domain_name`, `owner_id`, `asset_count`) VALUES")
    sql.append(",\n".join(product_values) + ";")
    sql.append("")

    # Asset Files
    sql.append("-- Asset Files")
    asset_values = []
    asset_id_counter = 1000
    
    # Tech Zone (Product 0)
    tech_root_id = asset_id_counter
    asset_id_counter += 1
    asset_values.append(f"({tech_root_id}, 0, 0, '/0/{tech_root_id}/', 1, '测试技术及工艺专区', 1)")
    
    tech_children = [
        ("通用测试点", []),
        ("信创测试", []),
        ("质量专题", ["缺陷分析", "安全生产故障分析"]),
        ("测试案例分级", []),
        ("测试数据集", []),
        ("非功能测试", ["工作指引", "操作指南", "能力全景图"]),
        ("性能测试", ["工作指引", "性能档案", "测试问题库", "实践积累"]),
        ("自动化测试", ["优秀实践"]),
        ("稳定性测试", ["优秀实践"]),
        ("智能化测试", ["优秀实践"])
    ]
    
    for name, subs in tech_children:
        cid = asset_id_counter
        asset_id_counter += 1
        asset_values.append(f"({cid}, 0, {tech_root_id}, '/0/{tech_root_id}/{cid}/', 1, '{name}', 1)")
        
        for sub in subs:
            sid = asset_id_counter
            asset_id_counter += 1
            asset_values.append(f"({sid}, 0, {cid}, '/0/{tech_root_id}/{cid}/{sid}/', 1, '{sub}', 1)")

    # Mgmt Zone (Product 0)
    mgmt_root_id = asset_id_counter
    asset_id_counter += 1
    asset_values.append(f"({mgmt_root_id}, 0, 0, '/0/{mgmt_root_id}/', 1, '测试管理专区', 1)")
    
    mgmt_children = ["测试任务管理", "批次管理"]
    for name in mgmt_children:
        cid = asset_id_counter
        asset_id_counter += 1
        asset_values.append(f"({cid}, 0, {mgmt_root_id}, '/0/{mgmt_root_id}/{cid}/', 1, '{name}', 1)")

    # Product Zones (Product 1-80)
    product_folders = [
        "0.产品功能全景及功能测试指南",
        "1.产品架构及关联产品",
        "2.产品缺陷分析",
        "3.产品业务知识",
        "4.产品其他支持类文档"
    ]
    
    for pid in range(1, 81):
        for folder in product_folders:
            fid = asset_id_counter
            asset_id_counter += 1
            asset_values.append(f"({fid}, {pid}, 0, '/{pid}/{fid}/', 1, '{folder}', 1)")

    sql.append(f"INSERT INTO `asset_file` (`id`, `product_id`, `parent_id`, `tree_path`, `node_type`, `file_name`, `created_by`) VALUES")
    sql.append(",\n".join(asset_values) + ";")
    
    sql.append("")
    sql.append("SET FOREIGN_KEY_CHECKS = 1;")
    
    return "\n".join(sql)

if __name__ == "__main__":
    import sys
    # Ensure stdout uses utf-8
    if sys.version_info[0] >= 3:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    
    with open("init_data.sql", "w", encoding="utf-8") as f:
        f.write(generate_sql())
