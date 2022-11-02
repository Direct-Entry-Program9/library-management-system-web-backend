package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.MemberDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*", loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {

    @Resource(lookup = "java:/comp/env/jdbc/dep9_lms")
    private DataSource pool;

//    @Override
//    public void init() throws ServletException {
//        try {
//            InitialContext ctx = new InitialContext();
//            pool = (DataSource) ctx.lookup("jdbc/lms");
//        } catch (NamingException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query!=null && size!=null && page!=null){
                if (!size.matches("\\d+") || !page.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid Size or page");
                }else {
                    paginatedSearchMembers(query,Integer.parseInt(page),Integer.parseInt(size),response);
                }
            } else if (query != null) {
                searchMembers(query,response);
            } else if (page!= null & size!=null) {
                if (!size.matches("\\d+") || !page.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid Size or page");
                }else {
                    paginatedLoadMembers(Integer.parseInt(page),Integer.parseInt(size),response);
                }
            }
            else {
                loadAllMembers(response);
            }
        }else {

            Matcher matcher = Pattern.compile("/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})?/").matcher(request.getPathInfo());
            if (matcher.matches()){
                getMembersDetail(matcher.group(1),response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid URL");
            }
        }
    }

    private  void paginatedSearchMembers(String query, int page, int size, HttpServletResponse response) throws IOException {



        try(Connection connection = pool.getConnection()){
            PreparedStatement countStm = connection.prepareStatement("SELECT COUNT(id) AS count FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            query ='%'+query+'%';
            for (int i = 1; i < 5; i++) {
                countStm.setString(i,query);
                stm.setString(i,query);
            }
            stm.setInt(5,size);
            stm.setInt(6,(page-1)*size);

            ResultSet rst = countStm.executeQuery();
            rst.next();
            response.setIntHeader("X-Total-Count",rst.getInt("count"));

            ArrayList<MemberDTO> members = new ArrayList<>();

            System.out.println(stm);
            ResultSet rst2 = stm.executeQuery();
            while (rst2.next()){
                String id = rst2.getString("id");
                String name = rst2.getString("name");
                String address = rst2.getString("address");
                String contact = rst2.getString("contact");

                MemberDTO member = new MemberDTO(id, name, address, contact);
                System.out.println(member);
                members.add(member);
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    private void searchMembers(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            query = "%"+query+"%";

            for (int i = 1; i < 5; i++) {
                stm.setString(i,query);
            }

            ResultSet rst = stm.executeQuery();
            ArrayList<MemberDTO> members = new ArrayList<>();
            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));
            }
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"can't fetch search members");
        }
    }
    private void paginatedLoadMembers(int page,int size, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT COUNT(id) AS count FROM member");
            rst.next();
            int totalMembers = rst.getInt("count");
            response.setIntHeader("X-Total-Count",totalMembers);

            PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?");
            stm2.setInt(1,size);
            stm2.setInt(2,(page-1)*size);

            rst = stm2.executeQuery();
            ArrayList<MemberDTO> paginatedMembers = new ArrayList<>();

            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                paginatedMembers.add(new MemberDTO(id,name,address,contact));
            }
            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(paginatedMembers,response.getWriter());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    private void loadAllMembers(HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM member");

            ArrayList<MemberDTO> members = new ArrayList<>();
            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(members,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"can,t load all members");
        }
    }
    private void getMembersDetail(String userId, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id=?");
            stm.setString(1,userId);
            ResultSet rst = stm.executeQuery();

            if (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                response.setContentType("application/json");
                JsonbBuilder.create().toJson(new MemberDTO(id,name,address,contact),response.getWriter());

            }else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid member ID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Fail to fetch members");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            try {
                if (request.getContentType() == null || !request.getContentType().startsWith("application/json")){
                    throw new JsonbException("Invalid JSON");
                }

                MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
                if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")){
                    throw new JsonbException("Name is empty or invalid");
                } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new JsonbException("Contact is empty or invalid");
                } else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9,.:;/\\-]+")) {
                    throw new JsonbException("Address is empty or invalid");
                }

                try (Connection connection = pool.getConnection()) {
                    member.setId(UUID.randomUUID().toString());
                    PreparedStatement stm = connection.prepareStatement("INSERT INTO member (id,name,address,contact) VALUES (?,?,?,?)");
                    stm.setString(1,member.getId());
                    stm.setString(2,member.getName());
                    stm.setString(3,member.getAddress());
                    stm.setString(4,member.getContact());

                    int affectedRows = stm.executeUpdate();
                    if (affectedRows == 1){
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        JsonbBuilder.create().toJson(member,response.getWriter());
                    }else {
                        throw new SQLException("Something Went Wrong");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } catch (JsonbException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
            }
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()== null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})?/").matcher(request.getPathInfo());
        if (matcher.matches()){
            deleteMember(matcher.group(1),response);
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    private void deleteMember(String memberId, HttpServletResponse response) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1,memberId);
            int affectedRow = stm.executeUpdate();
            if (affectedRow==0){
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid Member ID");
            }else {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (SQLException|IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()== null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$").matcher(request.getPathInfo());
        if (matcher.matches()){
            updateMember(matcher.group(1),request,response);
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    private void updateMember(String memberId, HttpServletRequest request, HttpServletResponse response) throws IOException {


        try {
            if (request.getContentType()==null ||!request.getContentType().startsWith("application/json")){
                throw new JsonbException("Invalid JSON");
            }

            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
            if (member.getId() == null || !memberId.equalsIgnoreCase(member.getId())){
                throw new JsonbException("Id is empty or invalid");
            } else if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")){
                throw new JsonbException("Name is empty or invalid");
            } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty or invalid");
            } else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9,.:;/\\-]+")) {
                throw new JsonbException("Address is empty or invalid");
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("UPDATE member SET name=?, address=?, contact=? WHERE id=?");
                stm.setString(1,member.getName());
                stm.setString(2,member.getAddress());
                stm.setString(3,member.getContact());
                stm.setString(4,member.getId());

                int affectedRow = stm.executeUpdate();
                if (affectedRow==1){
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,"Member doesn't exist");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Fail to update member");
            }

        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
        }
    }
}
