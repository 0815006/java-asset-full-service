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
    # Password hash for '123456' (BCrypt) - using a placeholder or a known hash
    # Let's use the one from previous file: e10adc3949ba59abbe56e057f20f883e (MD5 for 123456)
    # But LoginController uses simple check or whatever. Let's stick to what was there.
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
    
    product_values = []
    for i in range(1, 81):
        team = teams[i % 5]
        domain = domains[i % 5]
        owner_id = 2 # Chendong
        asset_count = random.randint(5, 50)
        product_values.append(f"({i}, '产品{i}', '{team}', '{domain}', {owner_id}, {asset_count})")
    
    sql.append(f"INSERT INTO `product` (`id`, `product_name`, `team_name`, `domain_name`, `owner_id`, `asset_count`) VALUES")
    sql.append(",\n".join(product_values) + ";")
    sql.append("")

    # Asset Files
    sql.append("-- Asset Files")
    asset_values = []
    asset_id_counter = 1000
    
    # Helper to add asset
    def add_asset(id, pid, parent_id, name, type=1):
        # Calculate tree path. 
        # If parent_id is 0, path is /pid/id/
        # If parent_id is not 0, we need parent's path. 
        # This is hard in a simple script without tracking.
        # Let's assume a simple structure where we know the parent path.
        
        # For root nodes (parent_id=0): /pid/id/
        # For child nodes: parent_path + id/
        
        path = ""
        if parent_id == 0:
            path = f"/{pid}/{id}/"
        else:
            # Find parent in our list? No, too slow.
            # We can pass parent_path.
            pass
        return f"({id}, {pid}, {parent_id}, '{path}', {type}, '{name}', 1)"

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
            # Product folders are roots for that product
            asset_values.append(f"({fid}, {pid}, 0, '/{pid}/{fid}/', 1, '{folder}', 1)")

    sql.append(f"INSERT INTO `asset_file` (`id`, `product_id`, `parent_id`, `tree_path`, `node_type`, `file_name`, `created_by`) VALUES")
    sql.append(",\n".join(asset_values) + ";")
    
    sql.append("")
    sql.append("SET FOREIGN_KEY_CHECKS = 1;")
    
    return "\n".join(sql)

if __name__ == "__main__":
    with open("init_data.sql", "w", encoding="utf-8") as f:
        f.write(generate_sql())
